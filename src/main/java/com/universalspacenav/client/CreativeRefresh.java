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

    private static Object lastLevel;
    private static int countdown;

    public static void tick() {
        // Every time a world is joined, rebuild once about 5 seconds later: other mods (Gateways to Eternity, Silent Gear...)
        // also receive their data from the server after the tabs were built.
        Minecraft m = Minecraft.getInstance();
        if (m.level != lastLevel) {
            lastLevel = m.level;
            countdown = m.level == null ? 0 : 100;
        } else if (countdown > 0 && --countdown == 0) {
            pending = true;
        }
        if (!pending) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null || mc.player.connection == null) return; // try again next tick
            pending = false;
            var features = mc.player.connection.enabledFeatures();
            boolean perms = mc.player.canUseGameMasterBlocks();
            var access = mc.level.registryAccess();
            // The game (and ModernFix) skip a rebuild when features/permissions/registries look unchanged, so a plain
            // call does nothing. Rebuilding once with the opposite permission flag, then again with the real one, forces
            // a genuine rebuild both times.
            boolean a = CreativeModeTabs.tryRebuildTabContents(features, !perms, access);
            boolean b = CreativeModeTabs.tryRebuildTabContents(features, perms, access);
            String search = refreshSearch(mc, access);
            UniversalSpaceNavigation.LOGGER.info("[USN fix] creative rebuild results: " + a + "/" + b + ", search refresh: " + search);
        } catch (Throwable t) {
            pending = false;
            UniversalSpaceNavigation.LOGGER.warn("[USN fix] could not rebuild the creative tabs: " + t);
        }
    }

    /** Re-feeds the creative search box with the rebuilt tab contents (reflection, so a name change can only skip this step). */
    private static String refreshSearch(Minecraft mc, net.minecraft.core.HolderLookup.Provider access) {
        try {
            java.util.List<net.minecraft.world.item.ItemStack> items = new java.util.ArrayList<>(CreativeModeTabs.searchTab().getSearchTabDisplayItems());
            Object trees = mc.player.connection.searchTrees();
            String done = "items=" + items.size();
            for (java.lang.reflect.Method m : trees.getClass().getMethods()) {
                String n = m.getName();
                if (n.equals("updateCreativeTags") && m.getParameterCount() == 1) {
                    m.invoke(trees, items);
                    done += " tags";
                } else if (n.equals("updateCreativeTooltips") && m.getParameterCount() == 2) {
                    m.invoke(trees, access, items);
                    done += " tooltips";
                }
            }
            return done;
        } catch (Throwable t) {
            return "failed " + t;
        }
    }
}
