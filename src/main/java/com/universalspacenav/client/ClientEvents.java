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

    private static boolean isArmorOf(net.minecraft.world.item.ItemStack st, String classPart) {
        for (Class<?> c = st.getItem().getClass(); c != null; c = c.getSuperclass()) {
            if (c.getName().contains(classPart)) return true;
        }
        return false;
    }

    /** Display only. Counts worn Ad Astra space suit pieces; never grants or changes protection. */
    private static String protection(LocalPlayer p) {
        int n = 0;
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
                net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
            if (isArmorOf(p.getItemBySlot(slot), "adastra.common.items.armor.SpaceSuitItem")) n++;
        }
        if (n == 4) return "universalspacenav.protection.suit";
        if (n > 0) return "universalspacenav.protection.partial";
        return "universalspacenav.protection.none";
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
                Component.translatable("universalspacenav.hud.protection", Component.translatable(protection(p))),
        };
        for (int i = 0; i < lines.length; i++) {
            g.drawString(font, lines[i], px, py + i * 10, i == 0 ? 0xFFFFD27F : 0xFFFFFFFF, true);
        }
        g.pose().popPose();
    }
}
