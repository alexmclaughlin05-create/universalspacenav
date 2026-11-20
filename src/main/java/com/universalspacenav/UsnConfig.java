package com.universalspacenav;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class UsnConfig {
    /** Who opens the Ad Astra planet menu for a Viltrumite in Ad Astra mode. */
    public enum NativeTrigger { OURS, NATIVE }

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

        public final ModConfigSpec.BooleanValue vilt;
        public final ModConfigSpec.IntValue viltReserveSeconds;
        public final ModConfigSpec.IntValue viltRefillMultiplier;
        public final ModConfigSpec.BooleanValue viltVacuum;
        public final ModConfigSpec.BooleanValue viltCold;
        public final ModConfigSpec.BooleanValue marvel;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> marvelTags;
        public final ModConfigSpec.BooleanValue marvelNeedHelmetClosed;
        public final ModConfigSpec.DoubleValue marvelEnergyPerSecond;
        public final ModConfigSpec.EnumValue<NativeTrigger> viltTrigger;
        public final ModConfigSpec.BooleanValue suppressLanterns;
        public final ModConfigSpec.BooleanValue exo;
        public final ModConfigSpec.IntValue exoEnergyPerSecond;
        public final ModConfigSpec.BooleanValue hero;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> heroPowers;
        public final ModConfigSpec.BooleanValue heroBreathing;
        public final ModConfigSpec.BooleanValue heroVacuum;
        public final ModConfigSpec.BooleanValue heroCold;

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
            viltTrigger = b
                    .comment("Viltrumite Core opens Ad Astra's planet menu by itself when a Viltrumite passes its own height (default 1500).",
                            "In Arcana mode that is ALWAYS suppressed (Arcana does its own transition). Same while riding an Ad Astra rocket.",
                            "In Ad Astra mode: OURS = suppress Core's menu and use this mod's menu at the Ad Astra altitude; NATIVE = let Core's menu open and do not open a second one.")
                    .defineEnum("viltrumiteTriggerInAdAstraMode", NativeTrigger.OURS);
            suppressLanterns = b
                    .comment("In Arcana mode, stop Create: Lanterns - Additions from opening its Ad Astra planet menu for ring wearers.")
                    .define("suppressLanternsMenuInArcanaMode", true);
            b.pop();

            debug = b.comment("Log extra information.").define("debug", false);

            b.comment("Space protection. Each integration is independent. None of these ever apply to a player who is already protected by something else.").push("protection");
            b.push("viltrumite");
            vilt = b.comment("Players who have acquired the Viltrumite race (Viltrumite Core; also covers the Integrated Superheroes Omni-Man serum, which sets the same flag) get a breathing reserve in airless places.")
                    .define("enabled", true);
            viltReserveSeconds = b.comment("Length of the oxygen reserve in seconds (1800 = 30 minutes).")
                    .defineInRange("oxygenReserveSeconds", 1800, 1, 86400);
            viltRefillMultiplier = b.comment("Reserve seconds restored per real second while breathing normal air (10 = refills 30 minutes in 3 minutes).")
                    .defineInRange("refillMultiplier", 10, 1, 1000);
            viltVacuum = b.comment("Immune to Arcana's vacuum exposure (freezing) in space, even with an empty reserve.")
                    .define("vacuumResistance", true);
            viltCold = b.comment("Immune to Ad Astra's extreme-cold effect.")
                    .define("extremeColdResistance", true);
            b.pop();
            b.push("marvelSuits");
            marvel = b.comment("Sealed, powered Marvel Superheroes suits supply air in airless places.").define("enabled", true);
            marvelTags = b.comment("Item tags of complete suits that count as space suits. Default is the suit family Marvel Superheroes itself treats as its space suit (Iron Man Mark 39).")
                    .defineListAllowEmpty("suitTags", java.util.List.of("marvel:iron_man_mark_39_armor"), () -> "marvel:iron_man_mark_39_armor", o -> o instanceof String);
            marvelNeedHelmetClosed = b.comment("The helmet must be closed for the suit to be sealed.").define("requireHelmetClosed", true);
            marvelEnergyPerSecond = b.comment("Extra suit energy used per second while the suit is supplying air. 0 = no extra cost (the suit's own flight costs are never changed). Every piece must still hold some energy.")
                    .defineInRange("energyPerSecond", 0.0D, 0.0D, 1000.0D);
            b.pop();
            b.push("neuralExosuitAccessory");
            exo = b.comment("The Exosuit Life-Support Module (worn in a Curios charm slot) supplies air while a Neural Exosuit with energy is worn.")
                    .define("enabled", true);
            exoEnergyPerSecond = b.comment("Exosuit energy (FE) used per second while supplying air. Goes through the exosuit's own energy spending, so its Cell module discount applies.")
                    .defineInRange("energyPerSecond", 20, 0, 1000000);
            b.pop();
            b.push("superheroPowers");
            hero = b.comment("Lets the Integrated Superheroes powers listed below receive space protection. OFF by default.")
                    .define("enabled", false);
            heroPowers = b.comment("Power ids (Integrated Superheroes), e.g. \"binary_carol_danvers\", \"gamma_mutation_bruce_banner\". Empty by default. Nothing is granted to a power that is not listed.")
                    .defineListAllowEmpty("powers", java.util.List.of(), () -> "binary_carol_danvers", o -> o instanceof String);
            heroBreathing = b.comment("Listed powers breathe in airless places (no reserve, no cost).").define("breathing", true);
            heroVacuum = b.comment("Listed powers ignore Arcana's vacuum exposure.").define("vacuumResistance", true);
            heroCold = b.comment("Listed powers ignore Ad Astra's extreme cold.").define("extremeColdResistance", true);
            b.pop();
            b.pop();
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
