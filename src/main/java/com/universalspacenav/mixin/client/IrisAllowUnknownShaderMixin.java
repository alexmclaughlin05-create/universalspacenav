package com.universalspacenav.mixin.client;

import com.universalspacenav.client.SkyBuffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Iris + shader pack compatibility. With a shader pack active and "allow unknown shaders" off, Iris masks colour and
 * depth writes for every shader that is not one of its own, which silently blanks Arcana's planet shader.
 * While (and only while) Arcana draws a planet layer, tell Iris unknown shaders are allowed.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.config.IrisConfig", remap = false)
public abstract class IrisAllowUnknownShaderMixin {
    @Inject(method = "shouldAllowUnknownShaders()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void usn$allow(CallbackInfoReturnable<Boolean> cir) {
        if (SkyBuffers.allowUnknownShader()) cir.setReturnValue(true);
    }
}
