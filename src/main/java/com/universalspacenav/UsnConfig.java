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
        public final ModConfigSpec.BooleanValue ring;
        public final ModConfigSpec.BooleanValue hero;
        public final ModConfigSpec.ConfigValue<java.util.List<? extends String>> heroPowers;
        public final ModConfigSpec.BooleanValue heroBreathing;
        public final ModConfigSpec.BooleanValue heroVacuum;
        public final ModConfigSpec.BooleanValue heroCold;
        public final ModConfigSpec.BooleanValue jeiMapFix;

        Common(ModConfigSpec.Builder b) {
            jeiMapFix = b.comment("Speeds up joining a world: while JEI Stuff scans villager trades, map trades skip the slow structure search. Real trades are not affected. Turn off if JEI Stuff's map trade pages look wrong.")
                    .define("compat.jeiStuffSkipMapSearch", true);
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
            b.push("lanternRing");
            ring = b.comment("A lit Create: Lanterns ring also gives life support and vacuum protection in Arcana's space, the same way it already does in Ad Astra's space.",
                            "The cost is the ring's own (its cosmonautics lifeSupportPerSecond, willpower efficiency and potency); this mod adds nothing extra. Not used in Ad Astra space, where the ring already works by itself.")
                    .define("enabled", true);
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
        public final ModConfigSpec.BooleanValue skyNoDepth;
        public final ModConfigSpec.BooleanValue skyOwnBuffer;
        public final ModConfigSpec.BooleanValue skyIrisAllowShader;
        public final ModConfigSpec.BooleanValue skyHideAtmo;
        public final ModConfigSpec.BooleanValue leakFix;
        public final ModConfigSpec.BooleanValue lagWatch;
        public final ModConfigSpec.BooleanValue quietGeckoMissing;
        public final ModConfigSpec.BooleanValue statueFix;
        public final ModConfigSpec.BooleanValue pastelLoginFix;
        public final ModConfigSpec.BooleanValue ironJetpacksTabFix;
        public final ModConfigSpec.BooleanValue leakWatch;
        public final ModConfigSpec.IntValue leakWatchDelay;

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
            b.push("compat");
            skyOwnBuffer = b.comment("Iris compatibility for Arcana's space sky. Iris replaces the game's shared draw queue while the world renders, and that queue ignores endBatch(), so Arcana's Earth and Moon are never drawn at the right moment.",
                            "When true (default) and Iris is installed, Arcana's planet layers are drawn through their own immediate buffer instead. No effect without Iris.")
                    .define("arcanaSkyOwnBuffer", true);
            skyIrisAllowShader = b.comment("Iris + shader pack compatibility for Arcana's space sky. With a shader pack on, Iris switches off colour and depth writing for every shader it does not know, which includes Arcana's planet shader, so Earth and the Moon vanish.",
                            "When true (default), Iris is told to let unknown shaders draw, but only while Arcana's planet layers are being drawn. Everything else keeps your Iris setting. No effect without Iris or without a shader pack.")
                    .define("arcanaSkyAllowUnknownShader", true);
            skyHideAtmo = b.comment("With a shader pack active, Arcana's atmosphere layer (the thin blue glow around Earth) renders as a cyan wash with vertical stripes over the whole planet.",
                            "When true (default), that one layer is not drawn while a shader pack is on. Without a shader pack it is always drawn. Set false to bring it back.")
                    .define("arcanaSkyHideAtmosphereWithShaders", true);
            b.pop();
            b.push("diagnostics");
            skyNoDepth = b.comment("DIAGNOSTIC for Arcana's space sky. Draw the Earth and Moon surfaces without the depth test (nothing else changes). Also toggled in game with /usnsky nodepth.")
                    .define("disableSurfaceDepthTest", false);
            quietGeckoMissing = b.comment("Stops GeckoLib's 'Unable to find animation' error from flooding the log every frame (Creeper Overhaul dripstone creeper, Ars Elemental flashjack). Shows the first message, then at most one a minute. Does not change how mobs look.")
                    .define("compat.quietGeckolibMissingAnimation", true);
            statueFix = b.comment("Crash fix. If the Iron's Lib player statue pose animation was rejected by GeckoLib at startup, draw statues in their default pose instead of crashing the game.")
                    .define("compat.ironsLibStatueCrashFix", true);
            pastelLoginFix = b.comment("Server-join crash fix. Pastel's Knowledge Gem asks Apothic Enchanting for an enchantment level before enchantments are loaded when you join a server, which disconnects you with 'Network Protocol Error'. Answers level 0 for that early question only.")
                    .define("compat.pastelServerJoinFix", true);
            ironJetpacksTabFix = b.comment("Server fix. Iron Jetpacks' creative tab is filled from a list the server sends after you join; if it arrives after the tabs were built, the jetpacks are missing from the tab and JEI. Rebuilds the creative tabs once when the list arrives.")
                    .define("compat.ironJetpacksCreativeTabFix", true);
            lagWatch = b.comment("DIAGNOSTIC (read-only, writes to the log only when something is slow). Logs server ticks over 150 ms, game-thread stalls over 250 ms, garbage-collection time during them, and movement keys that were held but did not move the player (with where the delay happened). Also /usnlag status.")
                    .define("lagWatch", true);
            leakFix = b.comment("Memory leak workaround. Celestisynth keeps every player it ever animated in a permanent list, and Arcana's MobCasterClient keeps the last world it saw; both stop old worlds and players from being freed after dimension changes.",
                            "When true (default), USN removes only STALE entries (players removed from the game or from a world you are no longer in; a world that is not your current one). Entries for your current world are never touched. Mods other than these two are not affected.")
                    .define("leakFix", true);
            leakWatch = b.comment("DIAGNOSTIC (read-only). Watches the previous world and player after a dimension change with weak references (which do not keep them alive) and, after a delay, logs which known mod fields in Arcana, Create: Lanterns and Neural Exosuit still point at them. Cost: two reference checks per tick. Nothing is cleared or changed. Also /usnleak check.")
                    .define("leakWatch", true);
            leakWatchDelay = b.comment("Seconds to wait after a dimension change before the automatic leak scan.")
                    .defineInRange("leakWatchDelaySeconds", 10, 3, 300);
            b.pop();
        }
    }
}
