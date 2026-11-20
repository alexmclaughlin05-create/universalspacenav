package com.universalspacenav;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class UsnAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, UniversalSpaceNavigation.MODID);

    /** Each player's chosen space system. Saved with the player and kept on death. */
    public static final Supplier<AttachmentType<NavMode>> MODE = TYPES.register("nav_mode",
            () -> AttachmentType.builder(() -> NavMode.ARCANA)
                    .serialize(NavMode.CODEC)
                    .copyOnDeath()
                    .build());

    /** Remaining Viltrumite breathing reserve in ticks; -1 means "never used, treat as full". Resets on death. */
    public static final Supplier<AttachmentType<Integer>> OXYGEN = TYPES.register("viltrumite_oxygen",
            () -> AttachmentType.builder(() -> -1)
                    .serialize(com.mojang.serialization.Codec.INT)
                    .build());

    private UsnAttachments() {
    }
}
