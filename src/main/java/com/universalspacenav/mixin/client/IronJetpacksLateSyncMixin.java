package com.universalspacenav.mixin.client;

import com.universalspacenav.UsnConfig;
import com.universalspacenav.client.CreativeRefresh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Iron Jetpacks fills its creative tab from the jetpack list the server sends after you join. When that list arrives
 * after the game built the tabs, the tab (and JEI) misses every jetpack. This asks for the tabs to be rebuilt right
 * after the list is loaded.
 */
@Pseudo
@Mixin(targets = "com.blakebr0.ironjetpacks.registry.JetpackRegistry", remap = false)
public abstract class IronJetpacksLateSyncMixin {
    @Inject(method = "loadJetpacks(Lcom/blakebr0/ironjetpacks/network/payloads/SyncJetpacksPayload;)V", at = @At("TAIL"), require = 0, remap = false)
    private void usn$rebuildTabs(CallbackInfo ci) {
        try {
            if (UsnConfig.CLIENT.ironJetpacksTabFix.get()) CreativeRefresh.request();
        } catch (Throwable ignored) {
        }
    }
}
