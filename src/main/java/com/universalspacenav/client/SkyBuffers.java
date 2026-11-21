package com.universalspacenav.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;

/**
 * Iris compatibility for Arcana's space sky. While the level renders, Iris hands out a "fully buffered" draw queue
 * whose endBatch(RenderType) does nothing, so Arcana's immediate planet draws are deferred to the wrong time.
 * This gives Arcana its own immediate buffer for the sky layers instead.
 */
public final class SkyBuffers {
    private static MultiBufferSource.BufferSource source;
    private static Boolean irisLoaded;
    private static ThreadLocal<Boolean> skipExtension;
    private static boolean looked;

    private SkyBuffers() {
    }

    public static boolean active() {
        try {
            if (irisLoaded == null) irisLoaded = ModList.get().isLoaded("iris");
            return irisLoaded && UsnConfig.CLIENT.skyOwnBuffer.get();
        } catch (Throwable t) {
            return false;
        }
    }

    public static MultiBufferSource.BufferSource source() {
        if (source == null) {
            source = MultiBufferSource.immediate(new ByteBufferBuilder(786432));
            UniversalSpaceNavigation.LOGGER.info("[USN sky] Iris detected: drawing Arcana's planets through an immediate buffer");
        }
        return source;
    }

    /** With a shader pack active, Iris extends vertex formats; Arcana's own shader needs the plain format. */
    @SuppressWarnings("unchecked")
    public static void extension(boolean skip) {
        try {
            if (!looked) {
                looked = true;
                Field f = Class.forName("net.irisshaders.iris.vertices.ImmediateState").getField("skipExtension");
                skipExtension = (ThreadLocal<Boolean>) f.get(null);
            }
            if (skipExtension != null) skipExtension.set(skip);
        } catch (Throwable ignored) {
        }
    }
}
