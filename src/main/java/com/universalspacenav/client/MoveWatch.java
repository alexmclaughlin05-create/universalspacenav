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

    private static final String[] STATES = {"walking", "swimming", "flying", "airborne", "stationary"};
    private static final long[] slowByState = new long[5];
    private static final long[] okByState = new long[5];
    private static String pState = "";
    private static long pLatencyMs;

    private MoveWatch() {
    }

    /** walking = on ground; swimming = in fluid; flying = creative/elytra flag or fast upward boost; airborne = jumping/falling. */
    private static int classify(LocalPlayer p) {
        if (p.isInWater() || p.isInLava()) return 1;
        if (p.getAbilities().flying || p.isFallFlying() || (!p.onGround() && p.getDeltaMovement().y > 0.45)) return 2;
        if (!p.onGround()) return 3;
        return 0;
    }

    public static void onCorrection() {
        corrections++;
    }

    public static void tick() {
        if (!LagWatch.enabled()) return;
        long now = System.nanoTime();
        long gc = LagWatch.gcMs();
        ticks++;
        Minecraft mc0 = Minecraft.getInstance();
        if (mc0.player == null || mc0.level == null) {
            lastTickNs = 0;
            pending = false;
            return;
        }
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
            pState = STATES[classify(p)];
            return;
        }
        if (!pending) return;
        if (fin && !pSawImpulse) {
            pSawImpulse = true;
            pImpulseNs = now;
        }
        double dx = pos.x - sx, dz = pos.z - sz;
        boolean moved = dx * dx + dz * dz > 0.0025;
        
        long heldMs = (now - pStartNs) / 1_000_000;
        if (moved) {
            int si = java.util.Arrays.asList(STATES).indexOf(pState);
            if (si < 0) si = 4;
            if (heldMs > 300) {
                slowMoves++;
                slowByState[si]++;
                report("slow horizontal start", heldMs, now, gc);
            } else {
                okByState[si]++;
            }
            pending = false;
        } else if (!raw) {
            if (heldMs > 150) {
                slowMoves++;
                int si = java.util.Arrays.asList(STATES).indexOf(pState);
                slowByState[si < 0 ? 4 : si]++;
                report("key released before any horizontal movement", heldMs, now, gc);
            }
            pending = false;
        } else if (heldMs > 1500) {
            slowMoves++;
            int si = java.util.Arrays.asList(STATES).indexOf(pState);
            slowByState[si < 0 ? 4 : si]++;
            report("no horizontal movement after 1.5 s of holding", heldMs, now, gc);
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
        UniversalSpaceNavigation.LOGGER.info("[USN lag] MOVEMENT [" + pState.toUpperCase() + (pState.equals("flying") ? ", flight: horizontal response is up to the flight mod" : "") + (STATES[classify(p)].equals(pState) ? "" : ", became " + STATES[classify(p)]) + "] " + what + ": held " + heldMs + " ms; " + where
                + "; GC in that time " + (gc - pStartGc) + " ms; server position corrections " + (corrections - pStartCorrections)
                + "; flying=" + p.getAbilities().flying + " fallFlying=" + p.isFallFlying() + " onGround=" + p.onGround()
                + " inWater=" + p.isInWater() + " usingItem=" + p.isUsingItem() + " dim=" + p.level().dimension().location()
                + " fps=" + mc.getFps()
                + "; velocity=" + p.getDeltaMovement() + " horizontalCollision=" + p.horizontalCollision
                + " sneaking=" + p.isShiftKeyDown() + " sprinting=" + p.isSprinting() + " speedAttr=" + p.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                + " vehicle=" + p.getVehicle() + " blockAtFeet=" + p.level().getBlockState(p.blockPosition()).getBlock()
                + " blockBelow=" + p.level().getBlockState(p.blockPosition().below()).getBlock()
                + " effects=" + p.getActiveEffects().stream().map(e -> e.getEffect().getKey() == null ? "?" : e.getEffect().getKey().location().toString()).toList()
                + " mainHand=" + p.getMainHandItem().getItem() + " chest=" + p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).getItem()
                + " head=" + p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getItem()
                + " inputFwd=" + p.input.forwardImpulse + " inputLeft=" + p.input.leftImpulse + " jump=" + p.input.jumping);
    }

    public static String status() {
        return "server ticks " + LagWatch.serverTicks + " (slowest " + LagWatch.worstServerMs + " ms, " + LagWatch.slowServerTicks + " over 150 ms); "
                + "client ticks " + ticks + " (longest stall " + worstGapMs + " ms, " + slowGaps + " over 250 ms); "
                + "slow horizontal starts " + slowMoves + " (walking " + slowByState[0] + "/" + (slowByState[0] + okByState[0]) + ", swimming " + slowByState[1] + "/" + (slowByState[1] + okByState[1]) + ", flying " + slowByState[2] + "/" + (slowByState[2] + okByState[2]) + ", airborne " + slowByState[3] + "/" + (slowByState[3] + okByState[3]) + ")" + "; server position corrections " + corrections
                + "; total GC time " + LagWatch.gcMs() + " ms; memory " + LagWatch.heap();
    }

    public static void reset() {
        worstGapMs = 0;
        slowGaps = 0;
        slowMoves = 0;
        java.util.Arrays.fill(slowByState, 0);
        java.util.Arrays.fill(okByState, 0);
        corrections = 0;
        LagWatch.worstServerMs = 0;
        LagWatch.slowServerTicks = 0;
        LagWatch.serverTicks = 0;
    }
}
