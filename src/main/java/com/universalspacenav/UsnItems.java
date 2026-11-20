package com.universalspacenav;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class UsnItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(UniversalSpaceNavigation.MODID);

    /** Worn in a Curios charm slot. Does nothing by itself; SpaceProtection checks for it. */
    public static final DeferredItem<Item> EXOSUIT_LIFE_SUPPORT = ITEMS.register("exosuit_life_support",
            () -> new Item(new Item.Properties().stacksTo(1)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
                    tip.add(Component.translatable("tooltip.universalspacenav.exosuit_life_support"));
                }
            });

    private UsnItems() {
    }

    @EventBusSubscriber(modid = UniversalSpaceNavigation.MODID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Tabs {
        @SubscribeEvent
        public static void add(BuildCreativeModeTabContentsEvent e) {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(EXOSUIT_LIFE_SUPPORT.get());
        }
    }
}
