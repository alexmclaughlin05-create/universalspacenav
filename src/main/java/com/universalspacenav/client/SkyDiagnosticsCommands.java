package com.universalspacenav.client;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, value = Dist.CLIENT)
public final class SkyDiagnosticsCommands {
    private SkyDiagnosticsCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("usnsky")
                .then(Commands.literal("nodepth")
                        .executes(c -> {
                            boolean v = !UsnConfig.CLIENT.skyNoDepth.get();
                            UsnConfig.CLIENT.skyNoDepth.set(v);
                            c.getSource().sendSuccess(() -> Component.literal("[USN] Earth/Moon surfaces without depth test: " + v), false);
                            return 1;
                        })
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean v = BoolArgumentType.getBool(c, "on");
                            UsnConfig.CLIENT.skyNoDepth.set(v);
                            c.getSource().sendSuccess(() -> Component.literal("[USN] Earth/Moon surfaces without depth test: " + v), false);
                            return 1;
                        })))
                .then(Commands.literal("dump").executes(c -> {
                    SkyDiagnostics.requestDump();
                    c.getSource().sendSuccess(() -> Component.literal("[USN] will log the sky state on the next space frame (only inside arcana:space)"), false);
                    return 1;
                }))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("[USN] no-depth switch = " + SkyDiagnostics.noDepth()), false);
                    return 1;
                })));
    }
}
