package com.universalspacenav.mixin;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Join-time speed-up. JEI Stuff tests every villager trade when a world loads, and for map trades that means a full
 * structure search per test (about 3 s each with Wan Ancient Beasts). Only while that JEI Stuff scan is on the call stack,
 * this answers the search with the trader's own position, so the map offer still gets built (name, price) without
 * searching. Real trades by players never have JEI Stuff on the stack and are not touched.
 */
@Mixin(ServerLevel.class)
public abstract class JeiStuffMapSearchMixin {
    private static final StackWalker WALKER = StackWalker.getInstance();
    private static final AtomicInteger SKIPPED = new AtomicInteger();

    @Inject(method = "findNearestMapStructure", at = @At("HEAD"), cancellable = true, require = 0)
    private void usn$skipDuringJeiStuffScan(TagKey<Structure> tag, BlockPos pos, int radius, boolean skipKnown, CallbackInfoReturnable<BlockPos> cir) {
        try {
            if (!UsnConfig.COMMON.jeiMapFix.get()) return;
        } catch (Throwable t) {
            return;
        }
        boolean fromJeiStuff = WALKER.walk(s -> s.anyMatch(f -> f.getClassName().equals("cy.jdkdigital.jeistuff.recipe.TradeRecipes")));
        if (!fromJeiStuff) return;
        int n = SKIPPED.incrementAndGet();
        if (n == 1 || n % 25 == 0) {
            UniversalSpaceNavigation.LOGGER.info("[USN jei] skipped " + n + " structure searches during the JEI Stuff trade scan (e.g. " + tag.location() + ")");
        }
        cir.setReturnValue(pos);
    }
}
