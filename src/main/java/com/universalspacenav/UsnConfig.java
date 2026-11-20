package com.universalspacenav;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class UsnConfig {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ModConfigSpec> common = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private UsnConfig() {
    }

    public static final class Common {
        public final ModConfigSpec.IntValue arcanaAltitude;
        public final ModConfigSpec.IntValue adAstraAltitude;
        public final ModConfigSpec.EnumValue<NavMode> defaultMode;
        public final ModConfigSpec.IntValue cooldownTicks;
        public final ModConfigSpec.BooleanValue openAdAstraMenu;
        public final ModConfigSpec.BooleanValue bypassArcanaForRockets;
        public final ModConfigSpec.BooleanValue respectLanternsAdditions;
        public final ModConfigSpec.BooleanValue debug;

        Common(ModConfigSpec.Builder b) {
            b.push("altitudes");
            arcanaAltitude = b
                    .comment("Shown on the HUD only. Arcana's real entry height (Y 1500) is fixed inside Arcana and cannot be changed by this mod.")
                    .defineInRange("arcanaAltitude", 1500, 0, 100000);
            adAstraAltitude = b
                    .comment("Height at which a player in Ad Astra mode gets Ad Astra's planet destination menu.")
                    .defineInRange("adAstraAltitude", 5000, 1600, 100000);
            b.pop();

            b.push("behaviour");
            defaultMode = b
                    .comment("Mode given to a player who has never chosen one.")
                    .defineEnum("defaultMode", NavMode.ARCANA);
            cooldownTicks = b
                    .comment("Minimum ticks between mode switches and between planet menu openings.")
                    .defineInRange("cooldownTicks", 40, 0, 12000);
            openAdAstraMenu = b
                    .comment("Open Ad Astra's planet menu when a player in Ad Astra mode reaches the Ad Astra altitude.")
                    .define("openAdAstraMenu", true);
            bypassArcanaForRockets = b
                    .comment("Never let Arcana pull a player out of an Ad Astra rocket, whatever mode is selected.")
                    .define("bypassArcanaForRockets", true);
            respectLanternsAdditions = b
                    .comment("Do not open a second planet menu if Create: Lanterns - Additions has already opened one on this ascent.")
                    .define("respectLanternsAdditions", true);
            b.pop();

            debug = b.comment("Log extra information.").define("debug", false);
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue hudEnabled;
        public final ModConfigSpec.BooleanValue hudAlwaysVisible;
        public final ModConfigSpec.IntValue hudX;
        public final ModConfigSpec.IntValue hudY;
        public final ModConfigSpec.DoubleValue hudScale;

        Client(ModConfigSpec.Builder b) {
            b.push("hud");
            hudEnabled = b.comment("Show the navigation HUD.").define("enabled", true);
            hudAlwaysVisible = b
                    .comment("Show the HUD even when the player is not flying or high up.")
                    .define("alwaysVisible", false);
            hudX = b.comment("Pixels from the left edge.").defineInRange("x", 6, 0, 4000);
            hudY = b.comment("Pixels from the top edge.").defineInRange("y", 40, 0, 4000);
            hudScale = b.comment("Text scale.").defineInRange("scale", 1.0D, 0.5D, 3.0D);
            b.pop();
        }
    }
}
