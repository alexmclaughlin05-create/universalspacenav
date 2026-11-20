package com.universalspacenav.protect;

import com.universalspacenav.UniversalSpaceNavigation;
import com.universalspacenav.UsnAttachments;
import com.universalspacenav.UsnConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side space protection supplied by this mod (Viltrumite reserve, sealed Marvel suits).
 *
 * Rules that prevent double use and accidental immunity:
 *  - The hooks (Arcana mixin, Ad Astra oxygen listener) are READ-ONLY. They ask {@link #supplyAir}, which only
 *    records "this player used my air at tick N". Nothing is drained there, however many times a mod asks per tick.
 *  - Draining happens exactly once per {@link #tick} call, and only if a use was recorded in the last 10 ticks.
 *  - The hooks run only after the original mod (and every other protection that ran before ours) said "no air",
 *    so a player already protected by a suit, ring, Giselle Addon, etc. never touches our reserve or energy.
 */
public final class SpaceProtection {
    public static final int SRC_NONE = 0, SRC_VILT = 1, SRC_MARVEL = 2, SRC_EXO = 3, SRC_HERO = 4;
    // Bits sent to the HUD
    public static final int B_VILT_PASSIVE = 1, B_VILT_AIR = 2, B_MARVEL_READY = 4, B_MARVEL_AIR = 8,
            B_EXO_READY = 16, B_EXO_AIR = 32, B_HERO = 64;

    private static final Map<UUID, long[]> USED = new ConcurrentHashMap<>(); // {gameTime, source}

    private static Method viltIsViltrumite, viltHasChosen;
    private static boolean viltLooked;
    private static Object oxygenApi;
    private static Method oxygenHasAt;
    private static boolean oxyLooked;
    private static Method arcanaVacuum;
    private static boolean arcanaLooked;

    private SpaceProtection() {
    }

    // ---------------------------------------------------------------- environment

    /** Is the player's dimension one of Arcana's vacuum dimensions? */
    public static boolean arcanaVacuum(net.minecraft.world.level.Level level) {
        try {
            if (!arcanaLooked) {
                arcanaLooked = true;
                Class<?> c = Class.forName("dev.arcana.magic.realms.SpaceEnvironment");
                arcanaVacuum = c.getMethod("vacuum", net.minecraft.resources.ResourceKey.class);
            }
            return arcanaVacuum != null && (Boolean) arcanaVacuum.invoke(null, level.dimension());
        } catch (Throwable t) {
            return false;
        }
    }

    /** Raw Ad Astra environment oxygen at the player's eyes (does not fire any listener). Defaults to true if unknown. */
    private static boolean adAstraEnvHasAir(ServerPlayer p) {
        try {
            if (!oxyLooked) {
                oxyLooked = true;
                Class<?> api = Class.forName("earth.terrarium.adastra.api.systems.OxygenApi");
                oxygenApi = api.getField("API").get(null);
                oxygenHasAt = api.getMethod("hasOxygen", net.minecraft.world.level.Level.class, BlockPos.class);
            }
            if (oxygenApi == null) return true;
            BlockPos pos = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
            return (Boolean) oxygenHasAt.invoke(oxygenApi, p.level(), pos);
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean airlessNow(ServerPlayer p) {
        return arcanaVacuum(p.level()) || !adAstraEnvHasAir(p);
    }

    // ---------------------------------------------------------------- Viltrumite

    private static void lookViltrumite() {
        if (viltLooked) return;
        viltLooked = true;
        try {
            Class<?> c = Class.forName("dev.baranhan.viltrumitecore.util.ViltrumiteCorePlayer");
            viltIsViltrumite = c.getMethod("isViltrumite");
            viltHasChosen = c.getMethod("hasChosenRace");
        } catch (Throwable ignored) {
        }
    }

    /** True only for players who have actually become Viltrumite (race chosen or granted), not merely "default on". */
    public static boolean isViltrumite(ServerPlayer p) {
        if (!UsnConfig.COMMON.vilt.get()) return false;
        lookViltrumite();
        try {
            return viltIsViltrumite != null && (Boolean) viltIsViltrumite.invoke(p) && (Boolean) viltHasChosen.invoke(p);
        } catch (Throwable t) {
            return false;
        }
    }

    public static int reserveMax() {
        return UsnConfig.COMMON.viltReserveSeconds.get() * 20;
    }

    public static int reserve(ServerPlayer p) {
        int v = p.getData(UsnAttachments.OXYGEN);
        int max = reserveMax();
        return v < 0 ? max : Math.min(v, max);
    }

    public static void setReserve(ServerPlayer p, int v) {
        p.setData(UsnAttachments.OXYGEN, Math.max(0, Math.min(reserveMax(), v)));
    }

    // ---------------------------------------------------------------- Marvel sealed suits

    private static DataComponentType<Float> energyType;
    private static DataComponentType<Boolean> helmetOpenType;

    @SuppressWarnings("unchecked")
    private static void lookMarvel() {
        if (energyType != null) return;
        try {
            energyType = (DataComponentType<Float>) BuiltInRegistries.DATA_COMPONENT_TYPE
                    .get(ResourceLocation.fromNamespaceAndPath("marvel", "energy"));
            helmetOpenType = (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE
                    .get(ResourceLocation.fromNamespaceAndPath("marvel", "helmet_open"));
        } catch (Throwable ignored) {
        }
    }

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static boolean wearsSuit(ServerPlayer p, TagKey<Item> tag) {
        for (EquipmentSlot s : ARMOR) {
            if (!p.getItemBySlot(s).is(tag)) return false;
        }
        return true;
    }

    /** Lowest energy over the worn suit pieces, or -1 if no configured suit is worn / sealed. */
    public static float marvelSuitEnergy(ServerPlayer p) {
        if (!UsnConfig.COMMON.marvel.get()) return -1;
        lookMarvel();
        if (energyType == null) return -1;
        try {
            for (String t : UsnConfig.COMMON.marvelTags.get()) {
                ResourceLocation rl = ResourceLocation.tryParse(t);
                if (rl == null) continue;
                if (!wearsSuit(p, TagKey.create(Registries.ITEM, rl))) continue;
                if (UsnConfig.COMMON.marvelNeedHelmetClosed.get() && helmetOpenType != null) {
                    Boolean open = p.getItemBySlot(EquipmentSlot.HEAD).get(helmetOpenType);
                    if (open != null && open) return -1;
                }
                float min = Float.MAX_VALUE;
                for (EquipmentSlot s : ARMOR) {
                    Float e = p.getItemBySlot(s).get(energyType);
                    min = Math.min(min, e == null ? 0f : e);
                }
                return min;
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    private static void drainMarvel(ServerPlayer p, float amount) {
        if (amount <= 0 || energyType == null) return;
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        Float e = chest.get(energyType);
        if (e != null) chest.set(energyType, Math.max(0f, e - amount));
    }

    // ---------------------------------------------------------------- Neural Exosuit accessory (Curios)

    private static Method curiosGetInv, curiosFind;
    private static boolean curiosLooked;
    private static Method coreStack, coreEnergy, coreTotal, totalStored, totalCapacity, exoSpend;
    private static boolean exoLooked;

    private static boolean wearsExoAccessory(ServerPlayer p) {
        try {
            if (!curiosLooked) {
                curiosLooked = true;
                Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");
                curiosGetInv = api.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
                Class<?> handler = Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");
                curiosFind = handler.getMethod("findFirstCurio", Item.class);
            }
            if (curiosGetInv == null) return false;
            java.util.Optional<?> inv = (java.util.Optional<?>) curiosGetInv.invoke(null, p);
            if (inv.isEmpty()) return false;
            java.util.Optional<?> found = (java.util.Optional<?>) curiosFind.invoke(inv.get(), com.universalspacenav.UsnItems.EXOSUIT_LIFE_SUPPORT.get());
            return found.isPresent();
        } catch (Throwable t) {
            return false;
        }
    }

    private static void lookExo() {
        if (exoLooked) return;
        exoLooked = true;
        try {
            Class<?> cs = Class.forName("com.hunterarmor.CoreStorage");
            coreStack = cs.getMethod("coreStack", net.minecraft.world.entity.LivingEntity.class);
            coreEnergy = cs.getMethod("energy", ItemStack.class);
            coreTotal = cs.getMethod("energyTotal", ItemStack.class);
            Class<?> tot = Class.forName("com.hunterarmor.CoreStorage$EnergyTotal");
            totalStored = tot.getMethod("stored");
            totalCapacity = tot.getMethod("capacity");
            exoSpend = Class.forName("com.hunterarmor.Energy").getMethod("spend", ServerPlayer.class, int.class);
        } catch (Throwable ignored) {
        }
    }

    /** {stored, capacity} of the worn Neural Exosuit, or null if none / unreadable. */
    private static long[] exoEnergy(ServerPlayer p) {
        lookExo();
        if (coreStack == null) return null;
        try {
            ItemStack core = (ItemStack) coreStack.invoke(null, p);
            if (core == null || core.isEmpty()) return null;
            Object tot = coreTotal.invoke(null, core);
            return new long[]{(Long) totalStored.invoke(tot), (Long) totalCapacity.invoke(tot)};
        } catch (Throwable t) {
            return null;
        }
    }

    /** Accessory worn AND the exosuit has energy. */
    public static boolean exoReady(ServerPlayer p) {
        if (!UsnConfig.COMMON.exo.get() || !wearsExoAccessory(p)) return false;
        long[] e = exoEnergy(p);
        return e != null && e[0] > 0;
    }

    /** For the HUD: exosuit energy when the accessory is worn, else null. */
    public static long[] exoHud(ServerPlayer p) {
        if (!UsnConfig.COMMON.exo.get() || !wearsExoAccessory(p)) return null;
        return exoEnergy(p);
    }

    private static void drainExo(ServerPlayer p, int amount) {
        lookExo();
        if (amount <= 0 || exoSpend == null) return;
        try {
            exoSpend.invoke(null, p, amount);
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- Integrated Superheroes powers (optional, off by default)

    private static Method heroActive, heroId;
    private static boolean heroLooked;

    public static boolean heroListed(ServerPlayer p) {
        if (!UsnConfig.COMMON.hero.get()) return false;
        java.util.List<? extends String> list = UsnConfig.COMMON.heroPowers.get();
        if (list.isEmpty()) return false;
        try {
            if (!heroLooked) {
                heroLooked = true;
                Class<?> hp = Class.forName("com.craisinlord.integrated_superheroes.hero.HeroPowers");
                heroActive = hp.getMethod("activeHero", net.minecraft.world.entity.player.Player.class);
                heroId = Class.forName("com.craisinlord.integrated_superheroes.hero.HeroPower").getMethod("id");
            }
            if (heroActive == null) return false;
            Object power = heroActive.invoke(null, p);
            if (power == null) return false;
            String id = String.valueOf(heroId.invoke(power));
            String bare = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            for (String want : list) {
                String w = want.contains(":") ? want.substring(want.indexOf(':') + 1) : want;
                if (w.equalsIgnoreCase(bare)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    // ---------------------------------------------------------------- hooks (read-only)

    /**
     * Called by the hooks only when the environment is airless and nobody earlier supplied air.
     * Returns true if this mod supplies air right now, and records the use. Never drains.
     */
    public static boolean supplyAir(ServerPlayer p) {
        if (p.isCreative() || p.isSpectator()) return false;
        long now = p.level().getGameTime();
        float suit = marvelSuitEnergy(p);
        if (suit > 0f) {
            USED.put(p.getUUID(), new long[]{now, SRC_MARVEL});
            return true;
        }
        if (exoReady(p)) {
            USED.put(p.getUUID(), new long[]{now, SRC_EXO});
            return true;
        }
        if (UsnConfig.COMMON.heroBreathing.get() && heroListed(p)) {
            USED.put(p.getUUID(), new long[]{now, SRC_HERO});
            return true;
        }
        if (isViltrumite(p) && reserve(p) > 0) {
            USED.put(p.getUUID(), new long[]{now, SRC_VILT});
            return true;
        }
        return false;
    }

    /** Cold / vacuum-exposure immunity (no consumption). */
    public static boolean vacuumResistant(ServerPlayer p) {
        return (UsnConfig.COMMON.viltVacuum.get() && isViltrumite(p))
                || (UsnConfig.COMMON.heroVacuum.get() && heroListed(p));
    }

    public static boolean coldResistant(ServerPlayer p) {
        return (UsnConfig.COMMON.viltCold.get() && isViltrumite(p))
                || (UsnConfig.COMMON.heroCold.get() && heroListed(p));
    }

    // ---------------------------------------------------------------- once-per-interval accounting

    /** Call once per {@code elapsed} ticks. The only place reserve or suit energy changes. */
    public static void tick(ServerPlayer p, int elapsed) {
        long now = p.level().getGameTime();
        long[] u = USED.get(p.getUUID());
        boolean recent = u != null && now - u[0] <= 10;
        if (recent && u[1] == SRC_MARVEL) {
            drainMarvel(p, (float) (UsnConfig.COMMON.marvelEnergyPerSecond.get() * elapsed / 20.0));
            return;
        }
        if (recent && u[1] == SRC_EXO) {
            drainExo(p, Math.round(UsnConfig.COMMON.exoEnergyPerSecond.get() * elapsed / 20.0f));
            return;
        }
        if (recent && u[1] == SRC_HERO) return; // free, no accounting
        if (!isViltrumite(p)) return;
        int cur = reserve(p);
        if (recent && u[1] == SRC_VILT) {
            setReserve(p, cur - elapsed);
        } else if (cur < reserveMax() && !airlessNow(p)) {
            setReserve(p, cur + elapsed * UsnConfig.COMMON.viltRefillMultiplier.get());
        }
    }

    public static void forget(UUID id) {
        USED.remove(id);
    }

    /** Bits for the HUD. */
    public static int hudBits(ServerPlayer p) {
        int bits = 0;
        long now = p.level().getGameTime();
        long[] u = USED.get(p.getUUID());
        boolean recent = u != null && now - u[0] <= 10;
        if (isViltrumite(p)) {
            bits |= B_VILT_PASSIVE;
            if (recent && u[1] == SRC_VILT) bits |= B_VILT_AIR;
        }
        if (marvelSuitEnergy(p) >= 0) {
            bits |= B_MARVEL_READY;
            if (recent && u[1] == SRC_MARVEL) bits |= B_MARVEL_AIR;
        }
        if (exoReady(p)) {
            bits |= B_EXO_READY;
            if (recent && u[1] == SRC_EXO) bits |= B_EXO_AIR;
        }
        if (UsnConfig.COMMON.hero.get() && heroListed(p)) bits |= B_HERO;
        return bits;
    }

    static void debug(String msg, Object... args) {
        if (UsnConfig.COMMON.debug.get()) UniversalSpaceNavigation.LOGGER.info(msg, args);
    }
}
