package com.universalspacenav.net;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: which protection (if any) the server found active, for the HUD only. */
public record SyncProtectionPayload(int code) implements CustomPacketPayload {
    public static final Type<SyncProtectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "sync_protection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProtectionPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, SyncProtectionPayload::code, SyncProtectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
