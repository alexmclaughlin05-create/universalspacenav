package com.universalspacenav.mixin.client;

import com.universalspacenav.client.SkyDiagnostics;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * DIAGNOSTIC (off by default). When enabled, the Earth and Moon surface render types of Arcana's space sky
 * are built with NO_DEPTH_TEST instead of LEQUAL_DEPTH_TEST. The render type's own setup/clear restores the
 * GL state after every draw, so nothing leaks into other mods' rendering.
 */
@Pseudo
@Mixin(targets = "dev.arcana.magic.realms.client.SpaceSkyMaterial", remap = false)
public abstract class ArcanaSpaceSkyMaterialMixin {
    @Shadow(remap = false)
    private static Map<String, RenderType> TYPES;

    private static RenderStateShard.DepthTestStateShard usn$none;

    @Inject(method = "material(Ljava/lang/String;I)Lnet/minecraft/client/renderer/RenderType;",
            at = @At("HEAD"), remap = false, require = 0)
    private static void usn$enter(String name, int mode, CallbackInfoReturnable<RenderType> cir) {
        SkyDiagnostics.currentMode = mode;
        try {
            if (SkyDiagnostics.consumeChanged() && TYPES != null) TYPES.clear();
        } catch (Throwable ignored) {
        }
    }

    @ModifyArg(method = "lambda$material$2(Ljava/lang/String;ILjava/lang/String;)Lnet/minecraft/client/renderer/RenderType;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder;setDepthTestState(Lnet/minecraft/client/renderer/RenderStateShard$DepthTestStateShard;)Lnet/minecraft/client/renderer/RenderType$CompositeState$CompositeStateBuilder;"),
            index = 0, remap = false, require = 0)
    private static RenderStateShard.DepthTestStateShard usn$depth(RenderStateShard.DepthTestStateShard original) {
        try {
            int mode = SkyDiagnostics.currentMode;
            if ((mode == 0 || mode == 1) && SkyDiagnostics.noDepth()) {
                if (usn$none == null) {
                    Field f = RenderStateShard.class.getDeclaredField("NO_DEPTH_TEST");
                    f.setAccessible(true);
                    usn$none = (RenderStateShard.DepthTestStateShard) f.get(null);
                }
                return usn$none;
            }
        } catch (Throwable ignored) {
        }
        return original;
    }
}
