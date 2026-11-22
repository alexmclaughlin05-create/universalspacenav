package com.universalspacenav.mixin.client;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Server-join crash fix. When a player joins a dedicated server, the game builds the creative inventory tabs while it
 * is still handling the login packet. Pastel's Knowledge Gem asks for an enchantment level there, and Apothic
 * Enchanting throws "Cannot access enchantment information before it has been loaded", which kills the connection
 * ("Network Protocol Error"). In single player the timing is different and it never happens. This answers "level 0"
 * for that one early question, so the tab is built normally and the login finishes.
 */
@Pseudo
@Mixin(targets = "earth.terrarium.pastel.helpers.enchantments.Ench", remap = false)
public abstract class PastelEnchantLoginMixin {
    private static boolean usn$logged;

    @Redirect(method = "lambda$getLevel$4", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getItemEnchantmentLevel(Lnet/minecraft/core/Holder;Lnet/minecraft/world/item/ItemStack;)I"), require = 0, remap = false)
    private static int usn$safeLevel(Holder<Enchantment> enchantment, ItemStack stack) {
        try {
            return EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
        } catch (UnsupportedOperationException e) {
            if (!UsnConfig.CLIENT.pastelLoginFix.get()) throw e;
            if (!usn$logged) {
                usn$logged = true;
                UniversalSpaceNavigation.LOGGER.warn("[USN fix] Pastel asked for an enchantment level before enchantments were loaded (server join); answering 0 so the login can finish");
            }
            return 0;
        }
    }
}
