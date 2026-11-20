package com.universalspacenav.client;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Read-only report of protections that OTHER mods already provide. Nothing here grants anything.
 * Every lookup is reflective, so a missing mod just means that line never applies.
 */
public final class ProtectionStatus {
    public static final String RING = "universalspacenav.protection.ring";
    public static final String SUIT_OK = "universalspacenav.protection.suit_ok";
    public static final String SUIT_EMPTY = "universalspacenav.protection.suit_empty";
    public static final String PARTIAL = "universalspacenav.protection.partial";
    public static final String NONE = "universalspacenav.protection.none";

    private static Method ringBreathe;
    private static boolean ringLooked;
    private static Method suitFull, suitOxygen;
    private static boolean suitLooked;

    private ProtectionStatus() {
    }

    public static String of(Player p) {
        if (ringBreathes(p)) return RING;
        int pieces = suitPieces(p);
        if (pieces == 4 && fullSet(p)) return hasSuitOxygen(p) ? SUIT_OK : SUIT_EMPTY;
        if (pieces > 0) return PARTIAL;
        return NONE;
    }

    /** Create: Lanterns ring life support (the same check Lanterns Additions feeds to Ad Astra). */
    private static boolean ringBreathes(Player p) {
        try {
            if (!ringLooked) {
                ringLooked = true;
                Class<?> c = Class.forName("io.github.vidarrcrib.createlanterns.ring.LifeSupport");
                ringBreathe = c.getMethod("canBreatheInSpace", Player.class);
            }
            return ringBreathe != null && (Boolean) ringBreathe.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void lookSuit() {
        if (suitLooked) return;
        suitLooked = true;
        try {
            Class<?> c = Class.forName("earth.terrarium.adastra.common.items.armor.SpaceSuitItem");
            suitFull = c.getMethod("hasFullSet", net.minecraft.world.entity.LivingEntity.class);
            suitOxygen = c.getMethod("hasOxygen", net.minecraft.world.entity.Entity.class);
        } catch (Throwable ignored) {
        }
    }

    private static boolean fullSet(Player p) {
        lookSuit();
        try {
            return suitFull != null && (Boolean) suitFull.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean hasSuitOxygen(Player p) {
        lookSuit();
        try {
            return suitOxygen != null && (Boolean) suitOxygen.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    private static int suitPieces(Player p) {
        int n = 0;
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            for (Class<?> c = p.getItemBySlot(s).getItem().getClass(); c != null; c = c.getSuperclass()) {
                if (c.getName().equals("earth.terrarium.adastra.common.items.armor.SpaceSuitItem")) {
                    n++;
                    break;
                }
            }
        }
        return n;
    }
}
