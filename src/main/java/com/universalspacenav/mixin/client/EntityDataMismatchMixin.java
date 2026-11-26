package com.universalspacenav.mixin.client;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnConfig;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * When the server sends one hidden mob value whose type does not match the slot the game has for it ("Invalid entity data
 * item type for field 15 ... old=CompoundTag new=String"), vanilla throws and you are disconnected. This happens when
 * several mods add hidden fields to every creature and the slot numbers end up in a different order on the client and
 * the server. This skips only that one value (and logs it) so you stay connected. The mob may lose one cosmetic value.
 */
@Mixin(SynchedEntityData.class)
public abstract class EntityDataMismatchMixin {
    private static int usn$count;

    @Inject(method = "assignValue", at = @At("HEAD"), cancellable = true, require = 0)
    private void usn$skipMismatch(SynchedEntityData.DataItem<?> target, SynchedEntityData.DataValue<?> entry, CallbackInfo ci) {
        try {
            if (!UsnConfig.CLIENT.entityDataMismatchFix.get()) return;
            if (!java.util.Objects.equals(entry.serializer(), target.getAccessor().serializer())) {
                if (usn$count++ < 20) {
                    UniversalSpaceNavigation.LOGGER.warn("[USN fix] skipped a mob data value the server sent for slot "
                            + entry.id() + " (client expects " + target.getAccessor().serializer() + ", server sent "
                            + entry.serializer() + "); another mod's hidden field is in a different slot on the server");
                }
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // anything unexpected: leave vanilla behaviour alone
        }
    }
}
