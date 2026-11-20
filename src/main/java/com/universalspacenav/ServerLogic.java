package com.universalspacenav;

import com.universalspacenav.net.CycleModePayload;
import com.universalspacenav.net.SyncModePayload;
import com.universalspacenav.net.SyncProtectionPayload;
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
    private static final Map<UUID, Integer> LAST_PROTECTION = new ConcurrentHashMap<>();
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
            r.playToClient(SyncProtectionPayload.TYPE, SyncProtectionPayload.CODEC, (payload, ctx) ->
                    ctx.enqueueWork(() -> {
                        com.universalspacenav.client.ClientState.protection = payload.code();
                        com.universalspacenav.client.ClientState.bits = payload.bits();
                        com.universalspacenav.client.ClientState.reserve = payload.reserve();
                        com.universalspacenav.client.ClientState.reserveMax = payload.reserveMax();
                        com.universalspacenav.client.ClientState.suitEnergy = payload.suitEnergy();
                        com.universalspacenav.client.ClientState.exoStored = payload.exoStored();
                        com.universalspacenav.client.ClientState.exoCap = payload.exoCap();
                    }));
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

        /** Explicitly carry navigation choice and oxygen reserve across non-death clones (e.g. returning from the End). */
        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone e) {
            if (e.isWasDeath()) return; // death: choice is copied by the attachment, reserve resets to full by design
            net.minecraft.world.entity.player.Player o = e.getOriginal(), n = e.getEntity();
            if (o.hasData(UsnAttachments.MODE)) n.setData(UsnAttachments.MODE, o.getData(UsnAttachments.MODE));
            if (o.hasData(UsnAttachments.OXYGEN)) n.setData(UsnAttachments.OXYGEN, o.getData(UsnAttachments.OXYGEN));
        }

        @SubscribeEvent
        public static void onCommands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
            e.getDispatcher().register(net.minecraft.commands.Commands.literal("usn")
                    .executes(ctx -> status(ctx.getSource()))
                    .then(net.minecraft.commands.Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                    .then(net.minecraft.commands.Commands.literal("reserve")
                            .requires(src -> src.hasPermission(2))
                            .then(net.minecraft.commands.Commands.argument("seconds",
                                            com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 86400))
                                    .executes(ctx -> {
                                        ServerPlayer sp = ctx.getSource().getPlayerOrException();
                                        int sec = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds");
                                        com.universalspacenav.protect.SpaceProtection.setReserve(sp, sec * 20);
                                        return status(ctx.getSource());
                                    }))));
        }

        private static int status(net.minecraft.commands.CommandSourceStack src) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
            ServerPlayer sp = src.getPlayerOrException();
            String reserve = "n/a";
            if (com.universalspacenav.protect.SpaceProtection.isViltrumite(sp)) {
                int s = com.universalspacenav.protect.SpaceProtection.reserve(sp) / 20;
                reserve = String.format("%d:%02d", s / 60, s % 60);
            }
            final String r = reserve;
            src.sendSuccess(() -> Component.translatable("universalspacenav.cmd.status",
                    Component.translatable(getMode(sp).translationKey()), r), false);
            return 1;
        }

        @SubscribeEvent
        public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
            UUID id = e.getEntity().getUUID();
            LAST_SWITCH.remove(id);
            LAST_MENU.remove(id);
            MENU_SHOWN.remove(id);
            LAST_PROTECTION.remove(id);
            com.universalspacenav.protect.SpaceProtection.forget(id);
        }

        @SubscribeEvent
        public static void onTick(PlayerTickEvent.Post e) {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (sp.tickCount % 5 != 0) return;
            tickProtection(sp);
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
        if (next == NavMode.AD_ASTRA) releaseLanterns(p);
        sync(p);
        p.displayClientMessage(Component.translatable("universalspacenav.message.mode",
                Component.translatable(next.translationKey())), true);
    }

    /** Account for our own sources once, then tell the client what the server finds (display only). */
    private static void tickProtection(ServerPlayer p) {
        try {
            com.universalspacenav.protect.SpaceProtection.tick(p, 5);
            int code = com.universalspacenav.client.ProtectionStatus.compute(p);
            int bits = com.universalspacenav.protect.SpaceProtection.hudBits(p);
            boolean vilt = (bits & com.universalspacenav.protect.SpaceProtection.B_VILT_PASSIVE) != 0;
            int reserve = vilt ? com.universalspacenav.protect.SpaceProtection.reserve(p) : -1;
            int max = vilt ? com.universalspacenav.protect.SpaceProtection.reserveMax() : 0;
            int suit = Math.round(com.universalspacenav.protect.SpaceProtection.marvelSuitEnergy(p));
            long[] exo = com.universalspacenav.protect.SpaceProtection.exoHud(p);
            int exoStored = exo == null ? -1 : (int) Math.min(Integer.MAX_VALUE, exo[0]);
            int exoCap = exo == null ? 0 : (int) Math.min(Integer.MAX_VALUE, exo[1]);
            int key = java.util.Objects.hash(code, bits, reserve / 20, suit, exoStored / 50, exoCap);
            Integer last = LAST_PROTECTION.put(p.getUUID(), key);
            if (last == null || last != key) {
                PacketDistributor.sendToPlayer(p, new SyncProtectionPayload(code, bits, reserve, max, suit, exoStored, exoCap));
            }
        } catch (Throwable t) {
            if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.warn("protection check failed", t);
        }
    }

    private static final String LANTERN_TAG = "clanternsadditions:opened_planet_menu";
    private static final String OUR_FLAG = "universalspacenav:suppressed_lanterns";
    private static java.util.function.Supplier<?> lanternHeight;
    private static boolean lanternLooked;

    private static double lanternSpaceHeight() {
        try {
            if (!lanternLooked) {
                lanternLooked = true;
                Class<?> c = Class.forName("nex.clanternsadditions.config.ServerConfig");
                lanternHeight = (java.util.function.Supplier<?>) c.getField("SPACE_HEIGHT").get(null);
            }
            return lanternHeight == null ? 5000.0 : ((Number) lanternHeight.get()).doubleValue();
        } catch (Throwable t) {
            return 5000.0;
        }
    }

    /** Arcana mode: pre-mark Lanterns Additions' menu as already opened so it does not pop up. Ad Astra mode: undo that. */
    private static void lanternHandling(ServerPlayer p) {
        var data = p.getPersistentData();
        if (getMode(p) == NavMode.ARCANA) {
            if (!UsnConfig.COMMON.suppressLanterns.get()) return;
            if (p.getY() >= lanternSpaceHeight() - 100 && !data.getBoolean(LANTERN_TAG)) {
                data.putBoolean(LANTERN_TAG, true);
                data.putBoolean(OUR_FLAG, true);
            } else if (data.getBoolean(OUR_FLAG) && !data.getBoolean(LANTERN_TAG)) {
                data.remove(OUR_FLAG); // Lanterns Additions cleared the tag itself (player came back down)
            }
        } else {
            releaseLanterns(p);
        }
    }

    /** Only removes the Lanterns tag if THIS mod set it. */
    public static void releaseLanterns(ServerPlayer p) {
        var data = p.getPersistentData();
        if (data.getBoolean(OUR_FLAG)) {
            data.remove(LANTERN_TAG);
            data.remove(OUR_FLAG);
        }
    }

    /** Another system (Viltrumite Core's native trigger) is opening the menu: do not open a second one on this ascent. */
    public static void markMenuShown(ServerPlayer p) {
        MENU_SHOWN.put(p.getUUID(), true);
    }

    private static void tickPlayer(ServerPlayer p) {
        lanternHandling(p);
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
            if (UsnConfig.COMMON.respectLanternsAdditions.get()) {
                p.getPersistentData().putBoolean(LANTERN_TAG, true); // so Lanterns Additions does not open a second menu
            }
            if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.info("Opened Ad Astra planet menu for {}", p.getName().getString());
        } catch (ClassNotFoundException ex) {
            if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.info("Ad Astra not installed; no planet menu.");
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("Could not open Ad Astra planet menu", t);
        }
    }
}
