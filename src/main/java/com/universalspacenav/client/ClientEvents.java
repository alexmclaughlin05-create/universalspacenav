package com.universalspacenav.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.universalspacenav.*;
import com.universalspacenav.net.CycleModePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    static final String CATEGORY = "key.categories.universalspacenav";
    public static final KeyMapping CYCLE = new KeyMapping("key.universalspacenav.cycle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    public static final KeyMapping TOGGLE_HUD = new KeyMapping("key.universalspacenav.toggle_hud",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

    private ClientEvents() {
    }

    @EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ModBus {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) {
            e.register(CYCLE);
            e.register(TOGGLE_HUD);
        }

        @SubscribeEvent
        public static void layers(RegisterGuiLayersEvent e) {
            e.registerAboveAll(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "hud"),
                    ClientEvents::renderHud);
        }
    }

    @EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, value = Dist.CLIENT)
    public static final class GameBus {
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            while (CYCLE.consumeClick()) {
                PacketDistributor.sendToServer(new CycleModePayload());
            }
            while (TOGGLE_HUD.consumeClick()) {
                UsnConfig.CLIENT.hudEnabled.set(!UsnConfig.CLIENT.hudEnabled.get());
            }
        }
    }

    private static String method(LocalPlayer p) {
        if (ServerLogic.inAdAstraRocket(p)) return "universalspacenav.method.rocket";
        if (p.isFallFlying()) return "universalspacenav.method.elytra";
        var chest = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem());
        String ns = chest.getNamespace(), path = chest.getPath();
        boolean flying = p.getAbilities().flying || (!p.onGround() && p.getDeltaMovement().y > 0.05);
        if (flying && path.contains("jetpack")) return "universalspacenav.method.jetpack";
        if (flying && ns.contains("exosuit")) return "universalspacenav.method.exosuit";
        if (p.getAbilities().flying) return "universalspacenav.method.flight";
        if (!p.onGround() && !p.isInWater()) return "universalspacenav.method.airborne";
        return "universalspacenav.method.none";
    }

    /** Everything currently protecting the player, in plain words. */
    private static Component sources() {
        java.util.List<Component> parts = new java.util.ArrayList<>();
        int code = ClientState.protection, bits = ClientState.bits;
        if (code == ProtectionStatus.RING || code == ProtectionStatus.ADDON || code == ProtectionStatus.SUIT_OK
                || code == ProtectionStatus.SUIT_EMPTY || code == ProtectionStatus.PARTIAL) {
            parts.add(Component.translatable(ProtectionStatus.key(code)));
        }
        if ((bits & com.universalspacenav.protect.SpaceProtection.B_MARVEL_AIR) != 0) {
            parts.add(Component.translatable("universalspacenav.src.marvel_air"));
        } else if ((bits & com.universalspacenav.protect.SpaceProtection.B_MARVEL_READY) != 0) {
            parts.add(Component.translatable("universalspacenav.src.marvel_ready"));
        }
        if ((bits & com.universalspacenav.protect.SpaceProtection.B_EXO_AIR) != 0) {
            parts.add(Component.translatable("universalspacenav.src.exo_air"));
        } else if ((bits & com.universalspacenav.protect.SpaceProtection.B_EXO_READY) != 0) {
            parts.add(Component.translatable("universalspacenav.src.exo_ready"));
        }
        if ((bits & com.universalspacenav.protect.SpaceProtection.B_HERO) != 0) {
            parts.add(Component.translatable("universalspacenav.src.hero"));
        }
        if ((bits & com.universalspacenav.protect.SpaceProtection.B_VILT_AIR) != 0) {
            parts.add(Component.translatable("universalspacenav.src.vilt_air"));
        } else if ((bits & com.universalspacenav.protect.SpaceProtection.B_VILT_PASSIVE) != 0) {
            parts.add(Component.translatable("universalspacenav.src.vilt_ready"));
        }
        if (parts.isEmpty()) return Component.translatable(ProtectionStatus.key(ProtectionStatus.NONE));
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(Component.literal(" + "));
            out.append(parts.get(i));
        }
        return out;
    }

    private static void renderHud(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return;
        if (!UsnConfig.CLIENT.hudEnabled.get()) return;
        boolean high = p.getY() > 300;
        if (!UsnConfig.CLIENT.hudAlwaysVisible.get() && !high && !p.getAbilities().flying && !p.isFallFlying()) return;

        NavMode mode = ClientState.mode;
        int entry = mode == NavMode.ARCANA ? UsnConfig.COMMON.arcanaAltitude.get() : UsnConfig.COMMON.adAstraAltitude.get();
        float s = UsnConfig.CLIENT.hudScale.get().floatValue();
        int x = UsnConfig.CLIENT.hudX.get();
        int y = UsnConfig.CLIENT.hudY.get();
        var font = mc.font;

        g.pose().pushPose();
        g.pose().scale(s, s, 1f);
        int px = Math.round(x / s), py = Math.round(y / s);
        Component[] lines = {
                Component.translatable("universalspacenav.hud.title"),
                Component.translatable("universalspacenav.hud.destination", Component.translatable(mode.translationKey())),
                Component.translatable("universalspacenav.hud.method", Component.translatable(method(p))),
                Component.translatable("universalspacenav.hud.entry", entry),
                Component.translatable("universalspacenav.hud.altitude", (int) p.getY()),
                Component.translatable("universalspacenav.hud.protection", sources()),
        };
        java.util.List<Component> all = new java.util.ArrayList<>(java.util.Arrays.asList(lines));
        if (ClientState.reserve >= 0) {
            int sec = ClientState.reserve / 20, max = ClientState.reserveMax / 20;
            all.add(Component.translatable("universalspacenav.hud.oxygen",
                    String.format("%d:%02d", sec / 60, sec % 60), String.format("%d:%02d", max / 60, max % 60)));
        }
        if (ClientState.suitEnergy >= 0) {
            all.add(Component.translatable("universalspacenav.hud.energy", ClientState.suitEnergy));
        }
        if (ClientState.exoStored >= 0) {
            all.add(Component.translatable("universalspacenav.hud.exo_energy", ClientState.exoStored, ClientState.exoCap));
        }
        for (int i = 0; i < all.size(); i++) {
            g.drawString(font, all.get(i), px, py + i * 10, i == 0 ? 0xFFFFD27F : 0xFFFFFFFF, true);
        }
        g.pose().popPose();
    }
}
