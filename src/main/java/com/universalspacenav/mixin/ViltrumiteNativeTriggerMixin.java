package com.universalspacenav.mixin;

import com.universalspacenav.NavMode;
import com.universalspacenav.ServerLogic;
import com.universalspacenav.UsnConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Viltrumite Core merges its own checkSpaceTransition(ServerPlayer) into Player; it runs "/adastra planets"
 * when a Viltrumite passes its own height (default 1500). We run after Core's mixin (higher priority number)
 * and make that follow the player's selected navigation mode.
 * If Viltrumite Core is absent or changes, require=0 means this simply does nothing.
 */
@Mixin(value = Player.class, priority = 2000)
public abstract class ViltrumiteNativeTriggerMixin {
    @Inject(method = "checkSpaceTransition(Lnet/minecraft/server/level/ServerPlayer;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void usn$nativeTrigger(ServerPlayer player, CallbackInfo ci) {
        try {
            if (ServerLogic.inAdAstraRocket(player) || ServerLogic.getMode(player) == NavMode.ARCANA) {
                ci.cancel(); // Arcana mode and rockets: no Ad Astra planet menu from this trigger
                return;
            }
            if (UsnConfig.COMMON.viltTrigger.get() == UsnConfig.NativeTrigger.OURS) {
                ci.cancel(); // Ad Astra mode: our menu at our altitude
            } else {
                ServerLogic.markMenuShown(player); // let Core's menu open, ours will not duplicate it
            }
        } catch (Throwable ignored) {
        }
    }
}
