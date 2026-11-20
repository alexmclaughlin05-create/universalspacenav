package com.universalspacenav.net;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, display only: protection code from other mods, this mod's source bits, Viltrumite
 * reserve (ticks, -1 = not Viltrumite), its maximum, and the lowest worn Marvel suit energy (-1 = none).
 */
public record SyncProtectionPayload(int code, int bits, int reserve, int reserveMax, int suitEnergy) implements CustomPacketPayload {
    public static final Type<SyncProtectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "sync_protection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProtectionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SyncProtectionPayload::code,
            ByteBufCodecs.VAR_INT, SyncProtectionPayload::bits,
            ByteBufCodecs.INT, SyncProtectionPayload::reserve,
            ByteBufCodecs.INT, SyncProtectionPayload::reserveMax,
            ByteBufCodecs.INT, SyncProtectionPayload::suitEnergy,
            SyncProtectionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
