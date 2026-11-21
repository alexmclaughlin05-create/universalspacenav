package com.universalspacenav.mixin.client;

import com.universalspacenav.client.MoveWatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DIAGNOSTIC: counts the times the server moves/corrects the local player's position (rubber-banding). Read-only. */
@Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
public abstract class ServerCorrectionMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0)
    private void usn$count(net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        MoveWatch.onCorrection();
    }
}
