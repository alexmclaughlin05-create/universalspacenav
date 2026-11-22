package com.universalspacenav.client;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;

import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Map;

/**
 * Memory leak workaround for two mods that keep old worlds/players alive after a dimension change (found with
 * /usnleak find):
 * - Celestisynth CSAnimator.animationData: a permanent player -> animation map that is never cleaned.
 * - Arcana MobCasterClient.level: the last world seen, only cleared on logout.
 * Only stale entries are removed: a player that has been removed or belongs to a world that is not the current one,
 * and a world that is not the current one. Everything for the current world is left alone. Runs once a second on the
 * client thread. Any failure is logged once and the fix switches itself off; it never throws into the game.
 */
public final class LeakFix {
    private static boolean looked;
    private static Map<Object, Object> animationData;
    private static Map<Object, Object> otherData;
    private static Map<Object, Object> mirroredData;
    private static Field mobCasterLevel;
    private static boolean broken;
    private static int ticks;
    private static long clearedPlayers;
    private static long clearedLevels;

    private LeakFix() {
    }

    public static void tick() {
        if (broken) return;
        if (++ticks % 20 != 0) return;
        try {
            if (!UsnConfig.CLIENT.leakFix.get()) return;
            Minecraft mc = Minecraft.getInstance();
            ClientLevel cur = mc.level;
            if (cur == null) return; // only act while a world is loaded, so we never race a dimension change
            if (!looked) look();
            Object me = mc.player;
            clean(animationData, me, cur);
            clean(otherData, me, cur);
            clean(mirroredData, me, cur);
            if (mobCasterLevel != null) {
                Object v = mobCasterLevel.get(null);
                if (v != null && v != cur) {
                    mobCasterLevel.set(null, null);
                    clearedLevels++;
                    UniversalSpaceNavigation.LOGGER.info("[USN leakfix] Arcana MobCasterClient: released a stale world (total " + clearedLevels + ")");
                }
            }
        } catch (Throwable t) {
            broken = true;
            UniversalSpaceNavigation.LOGGER.warn("[USN leakfix] failed, switching the leak workaround off for this session", t);
        }
    }

    private static void clean(Map<Object, Object> map, Object me, ClientLevel cur) {
        if (map == null) return;
        int n = 0;
        for (Iterator<Map.Entry<Object, Object>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Object k = it.next().getKey();
            if (k == me) continue;
            if (k instanceof AbstractClientPlayer p && (p.isRemoved() || p.level() != cur)) {
                it.remove();
                n++;
            }
        }
        if (n > 0) {
            clearedPlayers += n;
            UniversalSpaceNavigation.LOGGER.info("[USN leakfix] Celestisynth: released " + n + " stale player entr" + (n == 1 ? "y" : "ies") + " (total " + clearedPlayers + ")");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> grab(Class<?> c, String name) {
        try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null) instanceof Map<?, ?> m ? (Map<Object, Object>) m : null;
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void look() {
        looked = true;
        ClassLoader cl = LeakFix.class.getClassLoader();
        try {
            Class<?> c = Class.forName("org.thecelestialworkshop.celestisynth.api.animation.player.CSAnimator", false, cl);
            animationData = grab(c, "animationData");
            otherData = grab(c, "otherAnimationData");
            mirroredData = grab(c, "mirroredAnimationData");
            if (animationData != null) UniversalSpaceNavigation.LOGGER.info("[USN leakfix] Celestisynth animation lists found");
        } catch (ClassNotFoundException e) {
            // not installed
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN leakfix] Celestisynth list not usable: " + t);
        }
        try {
            Class<?> c = Class.forName("dev.arcana.magic.mobspells.client.MobCasterClient", false, cl);
            Field f = c.getDeclaredField("level");
            if (ClientLevel.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                mobCasterLevel = f;
                UniversalSpaceNavigation.LOGGER.info("[USN leakfix] Arcana MobCasterClient found");
            }
        } catch (ClassNotFoundException e) {
            // not installed
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN leakfix] Arcana MobCasterClient not usable: " + t);
        }
    }
}
