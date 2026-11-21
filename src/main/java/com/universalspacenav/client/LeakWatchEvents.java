package com.universalspacenav.client;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

/** Tick hook and /usnleak commands for the read-only LeakWatch diagnostic. */
@EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, value = Dist.CLIENT)
public final class LeakWatchEvents {
    private LeakWatchEvents() {
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        try {
            LeakWatch.tick();
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN leak] tick failed", t);
        }
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("usnleak")
                .then(Commands.literal("check").executes(c -> {
                    List<String> lines = LeakWatch.report(true, "manual check");
                    c.getSource().sendSuccess(() -> Component.literal("[USN leak] " + lines.get(0)), false);
                    int shown = 0;
                    for (int i = 1; i < lines.size() && shown < 8; i++, shown++) {
                        String s = lines.get(i);
                        c.getSource().sendSuccess(() -> Component.literal("[USN leak] " + s.trim()), false);
                    }
                    c.getSource().sendSuccess(() -> Component.literal("[USN leak] full result is in logs/latest.log (search \"USN leak\")"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("[USN leak] enabled=" + LeakWatch.enabled() + "; " + LeakWatch.status()), false);
                    return 1;
                }))
                .then(Commands.literal("gc")
                        .executes(c -> {
                            c.getSource().sendSuccess(() -> Component.literal("[USN leak] This forces a full garbage collection, which can freeze the game for a few seconds on a big heap. If you want it, run: /usnleak gc confirm"), false);
                            return 1;
                        })
                        .then(Commands.literal("confirm").executes(c -> {
                            c.getSource().sendSuccess(() -> Component.literal("[USN leak] running garbage collection now (may pause); result in about 2 seconds, see chat/log"), false);
                            LeakWatch.requestGc();
                            return 1;
                        }))));
    }
}
