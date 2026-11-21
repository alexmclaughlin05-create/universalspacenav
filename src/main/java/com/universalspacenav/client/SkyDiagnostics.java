package com.universalspacenav.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Client-only diagnostics for Arcana's space sky. Read-only except for the optional
 * "no depth test on planet surfaces" switch. Nothing here changes GL state; it only reads it.
 */
public final class SkyDiagnostics {
    /** Mode of the sky material being created right now (0 = Earth surface, 1 = Moon surface). */
    public static volatile int currentMode = -1;
    private static boolean lastApplied;
    private static volatile boolean dumpNext;
    private static boolean dumping;
    private static int dumped;

    private SkyDiagnostics() {
    }

    public static boolean noDepth() {
        try {
            return UsnConfig.CLIENT.skyNoDepth.get();
        } catch (Throwable t) {
            return false;
        }
    }

    /** True once when the switch changed, so the cached render types must be rebuilt. */
    public static boolean consumeChanged() {
        boolean now = noDepth();
        if (now != lastApplied) {
            lastApplied = now;
            return true;
        }
        return false;
    }

    public static void requestDump() {
        dumpNext = true;
    }

    public static void before(boolean earth, Vec3 centreRelative, double radius) {
        if (!dumpNext && !dumping) return;
        dumping = true;
        snapshot((earth ? "EARTH" : "MOON") + " before surface", centreRelative, radius, false);
    }

    public static void after(boolean earth, Vec3 centreRelative, double radius) {
        if (!dumping) return;
        snapshot((earth ? "EARTH" : "MOON") + " after all layers", centreRelative, radius, true);
        if (++dumped >= 2) {
            dumping = false;
            dumpNext = false;
            dumped = 0;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("[USN] sky dump written to latest.log"), false);
            }
        }
    }

    private static void snapshot(String tag, Vec3 centre, double radius, boolean color) {
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            int w = main.width, h = main.height;
            int fbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            StringBuilder sb = new StringBuilder("[USN sky] ").append(tag).append('\n');
            sb.append("  drawn-centre(scaled)=").append(centre).append(" radius(scaled)=").append(radius)
                    .append(" distance=").append(centre.length()).append('\n');
            sb.append("  framebuffer bound=").append(fbo).append(" main target=").append(main.frameBufferId)
                    .append(" size=").append(w).append('x').append(h).append('\n');
            sb.append("  depthTest=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                    .append(" depthFunc=0x").append(Integer.toHexString(GL11.glGetInteger(GL11.GL_DEPTH_FUNC)))
                    .append(" depthMask=").append(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK))
                    .append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE))
                    .append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND)).append('\n');
            var proj = RenderSystem.getProjectionMatrix();
            float m22 = proj.m22(), m32 = proj.m32();
            sb.append("  projection near=").append(m32 / (m22 - 1f)).append(" far=").append(m32 / (m22 + 1f)).append('\n');
            float[][] pts = {{0.5f, 0.5f}, {0.25f, 0.5f}, {0.75f, 0.5f}, {0.5f, 0.25f}, {0.5f, 0.75f}};
            sb.append("  depth at centre,left,right,low,high=");
            float[] d = new float[1];
            for (float[] p : pts) {
                GL11.glReadPixels((int) (w * p[0]), (int) (h * p[1]), 1, 1, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, d);
                sb.append(String.format("%.6f ", d[0]));
            }
            sb.append('\n');
            if (color) {
                int[] c = new int[1];
                GL11.glReadPixels(w / 2, h / 2, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, c);
                sb.append("  centre pixel RGBA=0x").append(String.format("%08X", c[0])).append('\n');
            }
            sb.append("  iris shader pack in use=").append(irisPack()).append(" noDepthSwitch=").append(noDepth());
            UniversalSpaceNavigation.LOGGER.info(sb.toString());
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN sky] snapshot failed", t);
        }
    }

    private static String irisPack() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            return String.valueOf(api.getMethod("isShaderPackInUse").invoke(inst));
        } catch (Throwable t) {
            return "n/a";
        }
    }
}
