package com.universalspacenav;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * DIAGNOSTIC (works on a client and on a dedicated server). Times every server tick and writes one log line only
 * when a tick takes more than 150 ms, together with the garbage-collection time that happened in that window.
 * Read-only: it changes nothing.
 */
@EventBusSubscriber(modid = UniversalSpaceNavigation.MODID)
public final class LagWatch {
    public static volatile long worstServerMs;
    public static volatile long slowServerTicks;
    public static volatile long serverTicks;
    private static long startNs;
    private static long startGc;

    private LagWatch() {
    }

    public static boolean enabled() {
        try {
            return UsnConfig.CLIENT.lagWatch.get();
        } catch (Throwable t) {
            return true;
        }
    }

    /** Total milliseconds the JVM has spent in garbage collection so far. */
    public static long gcMs() {
        long t = 0;
        try {
            for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
                long v = b.getCollectionTime();
                if (v > 0) t += v;
            }
        } catch (Throwable ignored) {
        }
        return t;
    }

    public static String heap() {
        Runtime r = Runtime.getRuntime();
        long used = r.totalMemory() - r.freeMemory();
        return (used >> 20) + " of " + (r.maxMemory() >> 20) + " MB (" + (100 * used / Math.max(1, r.maxMemory())) + "%)";
    }

    @SubscribeEvent
    public static void pre(ServerTickEvent.Pre e) {
        startNs = System.nanoTime();
        startGc = gcMs();
    }

    @SubscribeEvent
    public static void post(ServerTickEvent.Post e) {
        if (startNs == 0) return;
        long ms = (System.nanoTime() - startNs) / 1_000_000;
        serverTicks++;
        if (ms > worstServerMs) worstServerMs = ms;
        if (ms > 150 && enabled()) {
            slowServerTicks++;
            long gc = gcMs() - startGc;
            UniversalSpaceNavigation.LOGGER.info("[USN lag] SERVER tick took " + ms + " ms (garbage collection during it: " + gc + " ms"
                    + (gc * 2 >= ms ? ", so mostly GC" : ", so NOT mainly GC") + "; memory " + heap() + ")");
        }
    }
}
