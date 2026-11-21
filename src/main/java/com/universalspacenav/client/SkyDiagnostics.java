package com.universalspacenav.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;
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

    private static int[] frameBefore;
    private static boolean inLayer;
    private static int drawsInLayer;
    private static int query;

    /** Called at the start of a sky layer (shell). Only while a dump is running. */
    public static void layerBegin(int mode, String texture) {
        if (!dumping) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            RenderTarget main = mc.getMainRenderTarget();
            frameBefore = new int[main.width * main.height];
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frameBefore);
            inLayer = true;
            drawsInLayer = 0;
            query = GL15.glGenQueries();
            GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED, query);
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN sky] layerBegin failed", t);
            frameBefore = null;
        }
    }

    /** Called at the end of a sky layer. Reports how many fragments passed and how many pixels changed. */
    public static void layerEnd(int mode, String texture) {
        if (!dumping || frameBefore == null) return;
        try {
            inLayer = false;
            GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);
            int samples = GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT);
            GL15.glDeleteQueries(query);
            RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
            int[] after = new int[main.width * main.height];
            GL11.glReadPixels(0, 0, main.width, main.height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, after);
            long changed = 0, r = 0, g = 0, b = 0, a = 0;
            for (int i = 0; i < after.length; i++) {
                if (after[i] != frameBefore[i]) {
                    changed++;
                    int v = after[i];
                    r += v & 255; g += (v >> 8) & 255; b += (v >> 16) & 255; a += (v >>> 24);
                }
            }
            String name = mode == 0 ? "EARTH surface" : mode == 1 ? "MOON surface" : mode == 2 ? "clouds" : mode == 3 ? "atmosphere" : mode == 4 ? "starfield" : "mode " + mode;
            StringBuilder sb = new StringBuilder("[USN sky] LAYER ").append(name).append(" (").append(texture).append(")\n");
            sb.append("  draw calls inside this layer=").append(drawsInLayer).append("\n  fragments passed=").append(samples).append(" pixels changed=").append(changed);
            if (changed > 0) {
                sb.append(" avg RGBA of changed=").append(r / changed).append(',').append(g / changed).append(',').append(b / changed).append(',').append(a / changed);
            }
            if (mode == 0 || mode == 1) sb.append('\n').append(textureInfo(texture));
            UniversalSpaceNavigation.LOGGER.info(sb.toString());
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN sky] layerEnd failed", t);
        } finally {
            frameBefore = null;
        }
    }

    /** Reads the generated planet texture back and reports its size and average colour. Restores the texture binding. */
    private static String textureInfo(String name) {
        int saved = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            Class<?> fg = Class.forName("dev.arcana.magic.fire.client.FireGeometry");
            Object rl = fg.getMethod("texture", String.class).invoke(null, name);
            var tex = Minecraft.getInstance().getTextureManager().getTexture((net.minecraft.resources.ResourceLocation) rl);
            int id = tex.getId();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            int tw = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int th = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (tw <= 0 || th <= 0) return "  texture " + rl + " id=" + id + " size=" + tw + "x" + th + " (empty)";
            int[] px = new int[tw * th];
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
            long r = 0, g = 0, b = 0, a = 0;
            for (int v : px) { r += v & 255; g += (v >> 8) & 255; b += (v >> 16) & 255; a += (v >>> 24); }
            long n = px.length;
            return "  texture " + rl + " id=" + id + " size=" + tw + "x" + th + " avg RGBA=" + r / n + "," + g / n + "," + b / n + "," + a / n;
        } catch (Throwable t) {
            return "  texture lookup failed: " + t;
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, saved);
        }
    }

    public static boolean inLayer() {
        return dumping && inLayer;
    }

    /** Logs the GPU state just before a draw that happens inside a sky layer. Read-only. */
    public static void preDraw(String type, int vertices, int indices) {
        drawsInLayer++;
        try (MemoryStack st = MemoryStack.stackPush()) {
            var vp = st.mallocInt(4);
            var sc = st.mallocInt(4);
            var pm = st.mallocInt(2);
            var cm = st.malloc(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, sc);
            GL11.glGetIntegerv(GL11.GL_POLYGON_MODE, pm);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, cm);
            int prog = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            int link = prog > 0 ? GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) : -1;
            StringBuilder sb = new StringBuilder("[USN sky]   DRAW ").append(type).append('\n');
            sb.append("    vertices=").append(vertices).append(" indices=").append(indices)
                    .append(" program=").append(prog).append(" linked=").append(link).append('\n');
            sb.append("    viewport=").append(vp.get(0)).append(',').append(vp.get(1)).append(',').append(vp.get(2)).append(',').append(vp.get(3))
                    .append(" scissorTest=").append(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST))
                    .append(" scissorBox=").append(sc.get(0)).append(',').append(sc.get(1)).append(',').append(sc.get(2)).append(',').append(sc.get(3)).append('\n');
            sb.append("    colorMask=").append(cm.get(0)).append(cm.get(1)).append(cm.get(2)).append(cm.get(3))
                    .append(" stencilTest=").append(GL11.glIsEnabled(GL11.GL_STENCIL_TEST))
                    .append(" rasterizerDiscard=").append(GL11.glIsEnabled(GL30.GL_RASTERIZER_DISCARD))
                    .append(" polygonMode=0x").append(Integer.toHexString(pm.get(0)))
                    .append(" cull=").append(GL11.glIsEnabled(GL11.GL_CULL_FACE))
                    .append(" cullFace=0x").append(Integer.toHexString(GL11.glGetInteger(GL11.GL_CULL_FACE_MODE)))
                    .append(" frontFace=0x").append(Integer.toHexString(GL11.glGetInteger(GL11.GL_FRONT_FACE)))
                    .append(" depthTest=").append(GL11.glIsEnabled(GL11.GL_DEPTH_TEST))
                    .append(" blend=").append(GL11.glIsEnabled(GL11.GL_BLEND)).append('\n');
            sb.append("    GL error before draw=0x").append(Integer.toHexString(GL11.glGetError()));
            UniversalSpaceNavigation.LOGGER.info(sb.toString());
        } catch (Throwable t) {
            UniversalSpaceNavigation.LOGGER.warn("[USN sky] preDraw failed", t);
        }
    }

    public static void postDraw() {
        try {
            UniversalSpaceNavigation.LOGGER.info("[USN sky]   after draw GL error=0x" + Integer.toHexString(GL11.glGetError()));
        } catch (Throwable ignored) {
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
