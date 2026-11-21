package com.universalspacenav.client;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Method;

/**
 * Report of protections that OTHER mods already provide, worked out on the SERVER (so it matches what
 * actually applies) and sent to the client for display. Everything is read-only: nothing here grants,
 * consumes or changes anything. All lookups are reflective, so a missing mod simply never matches.
 * Only the translation keys and code numbers are used on the client.
 */
public final class ProtectionStatus {
    public static final int NONE = 0, PARTIAL = 1, SUIT_EMPTY = 2, SUIT_OK = 3, ADDON = 4, RING = 5;
    private static final String[] KEYS = {
            "universalspacenav.protection.none", "universalspacenav.protection.partial",
            "universalspacenav.protection.suit_empty", "universalspacenav.protection.suit_ok",
            "universalspacenav.protection.addon", "universalspacenav.protection.ring"};

    private static Method ringBreathe;
    private static boolean ringLooked;
    private static Object oxygenProof;
    private static Method proofDuration;
    private static boolean proofLooked;
    private static Method suitFull, suitOxygen;
    private static boolean suitLooked;

    private ProtectionStatus() {
    }

    public static String key(int code) {
        return KEYS[Math.max(0, Math.min(KEYS.length - 1, code))];
    }

    /** Server side. Highest-priority active protection wins. */
    public static int compute(ServerPlayer p) {
        if (ringBreathes(p) || com.universalspacenav.protect.SpaceProtection.ringProtects(p)) return RING;
        if (addonOxygenProof(p)) return ADDON;
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

    /**
     * Ad Astra: Giselle Addon oxygen proof (MekaSuit module, oxygen cans, etc.). Reads only the
     * remaining proof time on the player; it does NOT trigger the addon's own supply/consumption.
     * The addon only sets this once the player is somewhere without air, so it reads "none" until then.
     */
    private static boolean addonOxygenProof(Entity p) {
        try {
            if (!proofLooked) {
                proofLooked = true;
                Class<?> c = Class.forName("ad_astra_giselle_addon.common.registry.AddonProofs");
                oxygenProof = c.getField("OXYGEN").get(null);
                proofDuration = oxygenProof.getClass().getMethod("getProofDuration", Entity.class);
            }
            return proofDuration != null && (Integer) proofDuration.invoke(oxygenProof, p) > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void lookSuit() {
        if (suitLooked) return;
        suitLooked = true;
        try {
            Class<?> c = Class.forName("earth.terrarium.adastra.common.items.armor.SpaceSuitItem");
            suitFull = c.getMethod("hasFullSet", LivingEntity.class);
            suitOxygen = c.getMethod("hasOxygen", Entity.class);
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
