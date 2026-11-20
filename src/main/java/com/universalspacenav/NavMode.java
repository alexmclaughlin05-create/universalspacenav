package com.universalspacenav;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The space system a player has chosen to reach when flying upward. */
public enum NavMode implements StringRepresentable {
    ARCANA("arcana"),
    AD_ASTRA("ad_astra");

    public static final Codec<NavMode> CODEC = StringRepresentable.fromEnum(NavMode::values);

    private final String id;

    NavMode(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String translationKey() {
        return "universalspacenav.mode." + id;
    }

    public NavMode next() {
        NavMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static NavMode byOrdinal(int i) {
        NavMode[] all = values();
        return i >= 0 && i < all.length ? all[i] : ARCANA;
    }
}
