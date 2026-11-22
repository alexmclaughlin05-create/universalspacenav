package com.universalspacenav.mixin.client;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Crash fix. Iron's Lib player statues load a pose animation through GeckoLib. When GeckoLib has thrown that animation
 * file away at startup (it does that to any file holding one animation it cannot parse), drawing the statue throws and
 * the whole game crashes. Before posing a statue this checks that the animation file is really loaded; if it is not,
 * the statue is simply drawn in its default pose (the mod's own code already handles "no pose").
 */
@Pseudo
@Mixin(targets = "io.redspace.ironslib.statue.block.statue_block.StaticModel", remap = false)
public abstract class IronsLibStatueCrashMixin {
    private static final ResourceLocation USN_POSES = ResourceLocation.parse("irons_lib:animations/statue_poses.animation.json");
    private static Method usn$getBaked;
    private static boolean usn$lookedUp;
    private static boolean usn$logged;

    @Inject(method = "setupPose", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void usn$skipPoseIfMissing(CallbackInfo ci) {
        try {
            if (!UsnConfig.CLIENT.statueFix.get()) return;
            if (!usn$lookedUp) {
                usn$lookedUp = true;
                usn$getBaked = Class.forName("software.bernie.geckolib.cache.GeckoLibCache").getMethod("getBakedAnimations");
            }
            if (usn$getBaked == null) return;
            Object m = usn$getBaked.invoke(null);
            if (m instanceof Map<?, ?> map && !map.containsKey(USN_POSES)) {
                ci.cancel();
                if (!usn$logged) {
                    usn$logged = true;
                    UniversalSpaceNavigation.LOGGER.warn("[USN fix] Iron's Lib statue pose animation is not loaded (GeckoLib rejected the file); drawing statues in their default pose instead of crashing");
                }
            }
        } catch (Throwable t) {
            // never break rendering because of this check
        }
    }
}
