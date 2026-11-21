package com.universalspacenav.client;

import com.universalspacenav.LagWatch;
import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * DIAGNOSTIC (read-only). Separates the possible causes of "I pressed W and nothing happened for a second":
 * 1. the game thread was frozen (stall between client ticks, with garbage-collection time),
 * 2. the key was held but the final movement input stayed at zero (some mod overrides the input),
 * 3. the input was non-zero but the player did not move (server/physics/rubber-banding).
 * Logs only when a delay is longer than 300 ms. Holds no world or player references between ticks.
 */
public final class MoveWatch {
    private static long lastTickNs;
    private static long lastGc;
    private static long ticks;
    public static long worstGapMs;
    public static long slowGaps;
    public static long slowMoves;
    public static long corrections;

    // pending key press
    private static boolean pending;
    private static long pStartNs, pImpulseNs;
    private static long pStartGc, pStartCorrections;
    private static double sx, sz;
    private static boolean pSawImpulse;
    private static long pMaxGapMs;

    private MoveWatch() {
    }

    public static void onCorrection() {
        corrections++;
    }

    public static void tick() {
        if (!LagWatch.enabled()) return;
        long now = System.nanoTime();
        long gc = LagWatch.gcMs();
        ticks++;
        long gapMs = lastTickNs == 0 ? 0 : (now - lastTickNs) / 1_000_000;
        if (gapMs > worstGapMs) worstGapMs = gapMs;
        if (gapMs > 250) {
            slowGaps++;
            long d = gc - lastGc;
            UniversalSpaceNavigation.LOGGER.info("[USN lag] CLIENT game thread was stalled for " + gapMs + " ms between ticks (garbage collection: "
                    + d + " ms" + (d * 2 >= gapMs ? ", so mostly GC" : ", so NOT mainly GC") + "; memory " + LagWatch.heap() + ")");
        }
        if (pending && gapMs > pMaxGapMs) pMaxGapMs = gapMs;
        lastTickNs = now;
        lastGc = gc;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || p.isPassenger() || p.isSleeping()) {
            pending = false;
            return;
        }
        boolean raw = mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        boolean fin = p.input.forwardImpulse != 0 || p.input.leftImpulse != 0;
        Vec3 pos = p.position();
        if (raw && !pending) {
            pending = true;
            pStartNs = now;
            pStartGc = gc;
            pStartCorrections = corrections;
            sx = pos.x;
            sz = pos.z;
            pSawImpulse = false;
            pImpulseNs = 0;
            pMaxGapMs = 0;
            return;
        }
        if (!pending) return;
        if (fin && !pSawImpulse) {
            pSawImpulse = true;
            pImpulseNs = now;
        }
        double dx = pos.x - sx, dz = pos.z - sz;
        boolean moved = dx * dx + dz * dz > 1.0e-4;
        long heldMs = (now - pStartNs) / 1_000_000;
        if (moved) {
            if (heldMs > 300) {
                slowMoves++;
                report("slow start", heldMs, now, gc);
            }
            pending = false;
        } else if (!raw) {
            if (heldMs > 150) {
                slowMoves++;
                report("key released before the player moved (a missed or swallowed press)", heldMs, now, gc);
            }
            pending = false;
        } else if (heldMs > 1500) {
            slowMoves++;
            report("still not moving after 1.5 s of holding", heldMs, now, gc);
            pending = false;
        }
    }

    private static void report(String what, long heldMs, long now, long gc) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        long toImpulse = pSawImpulse ? (pImpulseNs - pStartNs) / 1_000_000 : -1;
        String where;
        if (!pSawImpulse) where = "the final movement input stayed ZERO the whole time although the key was down => a mod (or the game state) is overriding movement input";
        else if (toImpulse > 150) where = "the final movement input only became non-zero after " + toImpulse + " ms => something delayed the input";
        else if (pMaxGapMs > 250) where = "input was fine but the game thread stalled (" + pMaxGapMs + " ms) => freeze (see the CLIENT stall / SERVER tick lines)";
        else where = "input was non-zero from the start but the player did not move => movement blocked after input (server/physics/rubber-banding)";
        UniversalSpaceNavigation.LOGGER.info("[USN lag] MOVEMENT " + what + ": held " + heldMs + " ms; " + where
                + "; GC in that time " + (gc - pStartGc) + " ms; server position corrections " + (corrections - pStartCorrections)
                + "; flying=" + p.getAbilities().flying + " fallFlying=" + p.isFallFlying() + " onGround=" + p.onGround()
                + " inWater=" + p.isInWater() + " usingItem=" + p.isUsingItem() + " dim=" + p.level().dimension().location()
                + " fps=" + mc.getFps());
    }

    public static String status() {
        return "server ticks " + LagWatch.serverTicks + " (slowest " + LagWatch.worstServerMs + " ms, " + LagWatch.slowServerTicks + " over 150 ms); "
                + "client ticks " + ticks + " (longest stall " + worstGapMs + " ms, " + slowGaps + " over 250 ms); "
                + "slow/missed movement starts " + slowMoves + "; server position corrections " + corrections
                + "; total GC time " + LagWatch.gcMs() + " ms; memory " + LagWatch.heap();
    }

    public static void reset() {
        worstGapMs = 0;
        slowGaps = 0;
        slowMoves = 0;
        corrections = 0;
        LagWatch.worstServerMs = 0;
        LagWatch.slowServerTicks = 0;
        LagWatch.serverTicks = 0;
    }
}
