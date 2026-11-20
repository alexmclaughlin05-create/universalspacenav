package com.universalspacenav.mixin;

import com.universalspacenav.NavMode;
import com.universalspacenav.ServerLogic;
import com.universalspacenav.UsnConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops Arcana's automatic space transition for players who chose Ad Astra, and for anyone in an
 * Ad Astra rocket. Returning false means "Arcana did not handle this player".
 * Optional: if Arcana is missing or changes, require=0 and @Pseudo mean nothing breaks.
 */
@Pseudo
@Mixin(targets = "dev.arcana.magic.realms.SpaceTravel", remap = false)
public abstract class ArcanaSpaceTravelMixin {
    @Inject(method = "travelTick(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void usn$skip(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (player.level().dimension() != Level.OVERWORLD) return;
            boolean rocket = UsnConfig.COMMON.bypassArcanaForRockets.get() && ServerLogic.inAdAstraRocket(player);
            if (rocket || ServerLogic.getMode(player) == NavMode.AD_ASTRA) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
            // never break Arcana because of this mod
        }
    }
}
