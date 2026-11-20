package com.universalspacenav.mixin;

import com.universalspacenav.protect.SpaceProtection;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Arcana decides breathing AND vacuum freezing from SpaceEnvironment.protectedAir. We add our sources
 * after Arcana's own checks have all said "not protected", so nobody gets double protection.
 * Optional: @Pseudo + require=0, so a different Arcana version just means no effect, never a crash.
 */
@Pseudo
@Mixin(targets = "dev.arcana.magic.realms.SpaceEnvironment", remap = false)
public abstract class ArcanaSpaceEnvironmentMixin {
    @Inject(method = "protectedAir(Lnet/minecraft/server/level/ServerPlayer;)Z",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void usn$air(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!cir.getReturnValue() && SpaceProtection.arcanaVacuum(player.level()) && SpaceProtection.supplyAir(player)) {
                cir.setReturnValue(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** Vacuum exposure (the freezing damage) is skipped entirely for players with vacuum resistance. */
    @Inject(method = "exposure(Lnet/neoforged/neoforge/event/tick/PlayerTickEvent$Post;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void usn$exposure(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e, CallbackInfo ci) {
        try {
            if (e.getEntity() instanceof ServerPlayer sp && SpaceProtection.vacuumResistant(sp)) {
                if (sp.getTicksFrozen() > 0 && SpaceProtection.arcanaVacuum(sp.level())) sp.setTicksFrozen(0);
                ci.cancel();
            }
        } catch (Throwable ignored) {
        }
    }
}
