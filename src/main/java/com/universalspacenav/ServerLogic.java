package com.universalspacenav;

import com.universalspacenav.net.CycleModePayload;
import com.universalspacenav.net.SyncModePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** All decisions are made here on the server. The client only asks and displays. */
public final class ServerLogic {
    private static final Map<UUID, Long> LAST_SWITCH = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_MENU = new ConcurrentHashMap<>();
    /** Players who already got a menu on this ascent; cleared when they come back down. */
    private static final Map<UUID, Boolean> MENU_SHOWN = new ConcurrentHashMap<>();

    private ServerLogic() {
    }

    public static NavMode getMode(ServerPlayer p) {
        if (!p.hasData(UsnAttachments.MODE)) {
            NavMode def = UsnConfig.COMMON.defaultMode.get();
            p.setData(UsnAttachments.MODE, def);
            return def;
        }
        return p.getData(UsnAttachments.MODE);
    }

    /** True if the player is riding an Ad Astra rocket (found by registry name, no hard dependency). */
    public static boolean inAdAstraRocket(Entity player) {
        Entity v = player.getVehicle();
        if (v == null) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(v.getType());
        return key != null && key.getNamespace().equals("ad_astra") && key.getPath().contains("rocket");
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new SyncModePayload(getMode(p)));
    }

    @EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent e) {
            PayloadRegistrar r = e.registrar("1");
            r.playToClient(SyncModePayload.TYPE, SyncModePayload.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> com.universalspacenav.client.ClientState.mode = NavMode.byOrdinal(payload.mode())));
            r.playToServer(CycleModePayload.TYPE, CycleModePayload.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> {
                        if (ctx.player() instanceof ServerPlayer sp) cycle(sp);
                    }));
        }
    }

    @EventBusSubscriber(modid = UniversalSpaceNavigation.MODID)
    public static final class GameEvents {
        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
        }

        @SubscribeEvent
        public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
        }

        @SubscribeEvent
        public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
            if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            UUID id = e.getEntity().getUUID();
            LAST_SWITCH.remove(id);
            LAST_MENU.remove(id);
            MENU_SHOWN.remove(id);
        }

        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post e) {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 5 != 0) return;
            tickPlayer(sp);
        }
    }

    static void cycle(ServerPlayer p) {
        long now = p.serverLevel().getGameTime();
        Long last = LAST_SWITCH.get(p.getUUID());
        if (last != null && now - last < UsnConfig.COMMON.cooldownTicks.get()) return;
        LAST_SWITCH.put(p.getUUID(), now);
        NavMode next = getMode(p).next();
        p.setData(UsnAttachments.MODE, next);
        MENU_SHOWN.remove(p.getUUID());
        sync(p);
        p.displayClientMessage(Component.translatable("universalspacenav.message.mode",
                Component.translatable(next.translationKey())), true);
    }

    private static void tickPlayer(ServerPlayer p) {
        if (!UsnConfig.COMMON.openAdAstraMenu.get()) return;
        if (p.level().dimension() != Level.OVERWORLD) return;
        if (getMode(p) != NavMode.AD_ASTRA) return;
        if (inAdAstraRocket(p)) return; // rockets keep Ad Astra's own behaviour

        double y = p.getY();
        int entry = UsnConfig.COMMON.adAstraAltitude.get();
        if (y < entry - 200) {
            MENU_SHOWN.remove(p.getUUID());
            return;
        }
        if (y < entry || MENU_SHOWN.containsKey(p.getUUID())) return;
        if (p.isSpectator() || p.isPassenger()) return;

        long now = p.serverLevel().getGameTime();
        Long last = LAST_MENU.get(p.getUUID());
        if (last != null && now - last < UsnConfig.COMMON.cooldownTicks.get()) return;

        if (UsnConfig.COMMON.respectLanternsAdditions.get()
                && p.getPersistentData().getBoolean("clanternsadditions:opened_planet_menu")) {
            MENU_SHOWN.put(p.getUUID(), true);
            return;
        }
        LAST_MENU.put(p.getUUID(), now);
        MENU_SHOWN.put(p.getUUID(), true);
        openPlanetMenu(p);
    }

    private static void openPlanetMenu(ServerPlayer p) {
        try {
            Class<?> c = Class.forName("earth.terrarium.adastra.common.menus.base.PlanetsMenuProvider");
            Object provider = c.getConstructor().newInstance();
            Method m = c.getMethod("openMenu", ServerPlayer.class);
            m.invoke(provider, p);
            if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.info("Opened Ad Astra planet menu for {}", p.getName().getString());
        } catch (ClassNotFoundException ex) {
            if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.info("Ad Astra not installed; no planet menu.");
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("Could not open Ad Astra planet menu", t);
        }
    }
}
