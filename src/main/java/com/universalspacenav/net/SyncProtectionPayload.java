package com.universalspacenav.net;

import com.universalspacenav.UniversalSpaceNavigation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client, display only: protection code from other mods, this mod's source bits, Viltrumite
 * reserve (ticks, -1 = not Viltrumite) and its maximum, lowest worn Marvel suit energy (-1 = none),
 * exosuit stored energy (-1 = no accessory) and capacity.
 */
public record SyncProtectionPayload(int code, int bits, int reserve, int reserveMax, int suitEnergy,
                                    int exoStored, int exoCap) implements CustomPacketPayload {
    public static final Type<SyncProtectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(UniversalSpaceNavigation.MODID, "sync_protection"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncProtectionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.code);
                buf.writeVarInt(p.bits);
                buf.writeInt(p.reserve);
                buf.writeInt(p.reserveMax);
                buf.writeInt(p.suitEnergy);
                buf.writeInt(p.exoStored);
                buf.writeInt(p.exoCap);
            },
            buf -> new SyncProtectionPayload(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readInt(),
                    buf.readInt(), buf.readInt(), buf.readInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
