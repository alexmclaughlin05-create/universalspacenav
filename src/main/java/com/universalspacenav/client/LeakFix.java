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
            if (animationData != null) {
                int n = 0;
                for (Iterator<Map.Entry<Object, Object>> it = animationData.entrySet().iterator(); it.hasNext(); ) {
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

    @SuppressWarnings("unchecked")
    private static void look() {
        looked = true;
        ClassLoader cl = LeakFix.class.getClassLoader();
        try {
            Class<?> c = Class.forName("org.thecelestialworkshop.celestisynth.api.animation.player.CSAnimator", false, cl);
            Field f = c.getDeclaredField("animationData");
            f.setAccessible(true);
            Object m = f.get(null);
            if (m instanceof Map<?, ?> map) {
                animationData = (Map<Object, Object>) map;
                UniversalSpaceNavigation.LOGGER.info("[USN leakfix] Celestisynth animation list found");
            }
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
