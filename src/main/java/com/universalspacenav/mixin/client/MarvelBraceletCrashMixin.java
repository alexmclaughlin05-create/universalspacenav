package com.universalspacenav.mixin.client;

import com.universalspacenav.UsnConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marvel Legacy draws the item in your "bracelet" Curios slot in first person and casts it to its own interface without
 * checking. Any other mod's bracelet (e.g. Reliquified Artifacts' Onion Ring) crashes the game with a ClassCastException.
 * This makes Marvel Legacy see an empty slot when the bracelet is not one of its own items. Its own bracelets are untouched.
 */
@Pseudo
@Mixin(targets = "com.stxllarplanes.marvellegacy.util.CuriosSlotManager", remap = false)
public abstract class MarvelBraceletCrashMixin {
    private static Class<?> usn$render;
    private static Class<?> usn$hide;
    private static boolean usn$looked;
    private static boolean usn$warned;

    @Inject(method = "getStackBySlot(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void usn$onlyOwnItems(LivingEntity entity, String slot, CallbackInfoReturnable<ItemStack> cir) {
        try {
            if (!UsnConfig.CLIENT.marvelBraceletFix.get()) return;
            ItemStack stack = cir.getReturnValue();
            if (stack == null || stack.isEmpty()) return;
            if (!usn$looked) {
                usn$looked = true;
                ClassLoader cl = stack.getItem().getClass().getClassLoader();
                try { usn$render = Class.forName("com.stxllarplanes.marvellegacy.item.armor.IFirstPersonArmRender", false, cl); } catch (Throwable ignored) { }
                try { usn$hide = Class.forName("com.stxllarplanes.marvellegacy.item.armor.IArmorHideLayer", false, cl); } catch (Throwable ignored) { }
            }
            Item item = stack.getItem();
            if (usn$render == null) return; // cannot tell, leave the mod alone
            if (usn$render.isInstance(item) || (usn$hide != null && usn$hide.isInstance(item))) return;
            cir.setReturnValue(ItemStack.EMPTY);
            if (!usn$warned) {
                usn$warned = true;
                org.apache.logging.log4j.LogManager.getLogger("universalspacenav").warn("[USN fix] Marvel Legacy tried to draw a bracelet it does not own ({}); ignoring it to avoid a crash", item);
            }
        } catch (Throwable ignored) {
        }
    }
}
