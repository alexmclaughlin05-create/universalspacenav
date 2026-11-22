package com.universalspacenav.client;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Rebuilds the creative inventory tabs once, after a mod's data arrives late from a server. Used for Iron Jetpacks:
 * its tab is filled from a jetpack list the server sends AFTER the game has already built the tabs, so on a server the
 * tab only showed the 5 fixed items and no jetpacks (and JEI, which reads the tabs, had none either).
 */
public final class CreativeRefresh {
    private static volatile boolean pending;
    private static boolean logged;

    private CreativeRefresh() {
    }

    public static void request() {
        pending = true;
    }

    public static void tick() {
        if (!pending) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.player.connection == null) return; // try again next tick
            pending = false;
            CreativeModeTabs.tryRebuildTabContents(mc.player.connection.enabledFeatures(), mc.player.canUseGameMasterBlocks(), mc.level.registryAccess());
            if (!logged) {
                logged = true;
                UniversalSpaceNavigation.LOGGER.info("[USN fix] Rebuilt the creative tabs after late server data (Iron Jetpacks) arrived");
            }
        } catch (Throwable t) {
            pending = false;
            UniversalSpaceNavigation.LOGGER.warn("[USN fix] could not rebuild the creative tabs: " + t);
        }
    }
}
