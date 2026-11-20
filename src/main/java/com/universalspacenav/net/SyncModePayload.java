package com.universalspacenav.net;

import com.universalspacenav.NavMode;
import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: the player's current mode. */
public record SyncModePayload(int mode) implements CustomPacketPayload {
    public static final Type<SyncModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "sync_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncModePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SyncModePayload::mode, SyncModePayload::new);

    public SyncModePayload(NavMode m) {
        this(m.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
