package com.universalspacenav.mixin.client;

import com.universalspacenav.client.SkyDiagnostics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import com.universalspacenav.client.SkyBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only probes around Arcana's planet drawing, active only after /usnsky dump. */
@Pseudo
@Mixin(targets = "dev.arcana.magic.realms.client.SpaceSky", remap = false)
public abstract class ArcanaSpaceSkyMixin {
    @Inject(method = "planet(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DZDF)V",
            at = @At("HEAD"), remap = false, require = 0)
    private static void usn$before(Matrix4f m, Vec3 centre, double radius, boolean earth, double t, float p, CallbackInfo ci) {
        SkyDiagnostics.before(earth, centre, radius);
    }

    @Inject(method = "planet(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DZDF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private static void usn$after(Matrix4f m, Vec3 centre, double radius, boolean earth, double t, float p, CallbackInfo ci) {
        SkyDiagnostics.after(earth, centre, radius);
    }

    @Inject(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At("HEAD"), remap = false, require = 0)
    private static void usn$layerBegin(Matrix4f m, Vec3 c, double s, double r, int mode, String tex, double t, float p, CallbackInfo ci) {
        SkyDiagnostics.layerBegin(mode, tex);
    }

    @Inject(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private static void usn$layerEnd(Matrix4f m, Vec3 c, double s, double r, int mode, String tex, double t, float p, CallbackInfo ci) {
        SkyDiagnostics.layerEnd(mode, tex);
    }

    /** Iris compatibility: use our own immediate buffer for the sky layers (see SkyBuffers). */
    @Redirect(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderBuffers;bufferSource()Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;"),
            remap = false, require = 0)
    private static MultiBufferSource.BufferSource usn$source(RenderBuffers buffers) {
        return SkyBuffers.active() ? SkyBuffers.source() : buffers.bufferSource();
    }

    @Inject(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At("HEAD"), remap = false, require = 0)
    private static void usn$extOn(Matrix4f m, Vec3 c, double s, double r, int mode, String tex, double t, float p, CallbackInfo ci) {
        SkyBuffers.enter();
        if (SkyBuffers.active()) SkyBuffers.extension(true);
    }

    @Inject(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private static void usn$extOff(Matrix4f m, Vec3 c, double s, double r, int mode, String tex, double t, float p, CallbackInfo ci) {
        SkyBuffers.exit();
        if (SkyBuffers.active()) SkyBuffers.extension(false);
    }

    /** DIAGNOSTIC (/usnsky skip): leave one layer out to see which layer produces an artifact. Off by default. */
    @Inject(method = "shell(Lorg/joml/Matrix4f;Lnet/minecraft/world/phys/Vec3;DDILjava/lang/String;DF)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void usn$skip(Matrix4f m, Vec3 c, double s, double r, int mode, String tex, double t, float p, CallbackInfo ci) {
        if (SkyDiagnostics.skipMode == mode) {
            SkyBuffers.exit();
            if (SkyBuffers.active()) SkyBuffers.extension(false);
            SkyDiagnostics.layerEnd(mode, tex);
            ci.cancel();
        }
    }
}
