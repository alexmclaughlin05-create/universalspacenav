package com.universalspacenav.mixin.client;

import com.universalspacenav.client.SkyDiagnostics;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
}
