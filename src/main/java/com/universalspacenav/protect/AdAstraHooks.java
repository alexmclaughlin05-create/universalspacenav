package com.universalspacenav.protect;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Registers listeners with Ad Astra's own event lists without needing Ad Astra at compile time.
 * Registered at server start so ours come LAST: Ad Astra stops at the first listener that changes the result,
 * so any suit, ring or Giselle Addon proof that grants air has already answered before we are asked.
 */
@EventBusSubscriber(modid = UniversalSpaceNavigation.MODID)
public final class AdAstraHooks {
    private static boolean done;

    private AdAstraHooks() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent e) {
        if (done) return;
        done = true;
        try {
            Class<?> events = Class.forName("earth.terrarium.adastra.api.events.AdAstraEvents");
            register(Class.forName(events.getName() + "$EntityOxygenEvent"), (proxy, m, a) -> {
                if (!m.getName().equals("hasOxygen")) return objectMethod(proxy, m, a);
                Entity ent = (Entity) a[0];
                boolean has = (Boolean) a[1];
                if (!has && ent instanceof ServerPlayer sp && SpaceProtection.supplyAir(sp)) return true;
                return has;
            });
            register(Class.forName(events.getName() + "$ColdTemperatureTickEvent"), (proxy, m, a) -> {
                if (!m.getName().equals("tick")) return objectMethod(proxy, m, a);
                LivingEntity ent = (LivingEntity) a[1];
                // Ad Astra: returning false cancels the cold effect.
                if (ent instanceof ServerPlayer sp && SpaceProtection.coldResistant(sp)) return false;
                return true;
            });
            UniversalSpaceNavigation.LOGGER.info("Hooked Ad Astra oxygen and cold events.");
        } catch (ClassNotFoundException ex) {
            // Ad Astra not installed: nothing to hook.
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("Could not hook Ad Astra events", t);
        }
    }

    private static void register(Class<?> iface, InvocationHandler h) throws Exception {
        Object proxy = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, h);
        Method reg = iface.getMethod("register", iface);
        reg.invoke(null, proxy);
    }

    private static Object objectMethod(Object proxy, Method m, Object[] a) {
        return switch (m.getName()) {
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == a[0];
            case "toString" -> "UniversalSpaceNavigation hook";
            default -> null;
        };
    }
}
