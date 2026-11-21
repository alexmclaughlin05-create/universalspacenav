package com.universalspacenav.mixin.client;

import com.mojang.blaze3d.vertex.MeshData;
import com.universalspacenav.client.SkyDiagnostics;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Read-only probe: logs GPU state around draws made while an Arcana sky layer is being drawn (only during /usnsky dump). */
@Mixin(RenderType.class)
public abstract class RenderTypeDrawProbeMixin {
    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V"),
            remap = false, require = 0)
    private void usn$pre(MeshData mesh, CallbackInfo ci) {
        if (!SkyDiagnostics.inLayer()) return;
        var st = mesh.drawState();
        SkyDiagnostics.preDraw(this.toString(), st.vertexCount(), st.indexCount());
    }

    @Inject(method = "draw(Lcom/mojang/blaze3d/vertex/MeshData;)V", at = @At("RETURN"), remap = false, require = 0)
    private void usn$post(MeshData mesh, CallbackInfo ci) {
        if (SkyDiagnostics.inLayer()) SkyDiagnostics.postDraw();
    }
}
