package com.universalspacenav.net;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: please switch my destination. Carries no data, so nothing can be forged. */
public record CycleModePayload() implements CustomPacketPayload {
    public static final Type<CycleModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "cycle_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CycleModePayload> CODEC =
            StreamCodec.unit(new CycleModePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
