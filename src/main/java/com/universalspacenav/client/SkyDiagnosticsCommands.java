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
                .then(Commands.literal("skip")
                        .then(Commands.literal("off").executes(c -> {
                            SkyDiagnostics.skipMode = -1;
                            c.getSource().sendSuccess(() -> Component.literal("[USN] all Arcana sky layers are drawn again"), false);
                            return 1;
                        }))
                        .then(Commands.argument("layer", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 4)).executes(c -> {
                            int m = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "layer");
                            SkyDiagnostics.skipMode = m;
                            c.getSource().sendSuccess(() -> Component.literal("[USN] NOT drawing Arcana sky layer " + m
                                    + " (0 Earth surface, 1 Moon surface, 2 clouds, 3 atmosphere, 4 starfield/sun). Undo with /usnsky skip off"), false);
                            return 1;
                        })))
                .then(Commands.literal("allow")
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean v = BoolArgumentType.getBool(c, "on");
                            UsnConfig.CLIENT.skyIrisAllowShader.set(v);
                            c.getSource().sendSuccess(() -> Component.literal("[USN] Iris unknown-shader override for the planet shader: " + v), false);
                            return 1;
                        })))
                .then(Commands.literal("status").executes(c -> {
                    c.getSource().sendSuccess(() -> Component.literal("[USN] no-depth switch = " + SkyDiagnostics.noDepth() + ", skipped layer = " + SkyDiagnostics.skipMode + ", iris override = " + UsnConfig.CLIENT.skyIrisAllowShader.get()), false);
                    return 1;
                })));
    }
}
