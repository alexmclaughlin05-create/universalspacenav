# Universal Space Navigation 0.1.0 (NeoForge 1.21.1)

Press **J** (rebind in Controls > Universal Space Navigation) to switch your space destination:
- **Arcana**: Arcana behaves exactly as normal (its transition at Y 1500).
- **Ad Astra**: Arcana's automatic transition is skipped for you; at Y 5000 Ad Astra's planet menu opens.

Ad Astra rockets always use Ad Astra's own system. The mod grants no flight and no atmospheric protection.

## Install
Put the jar in `mods/` on the server and every client. Needs NeoForge 21.1+. Arcana and Ad Astra are optional.
Configs: `config/universalspacenav-common.toml` (altitudes, default mode, cooldown, integrations, debug) and `-client.toml` (HUD).

## Flight methods
Anything that lets you rise: it works by height, not by item, so Create Lanterns, Viltrumite, superhero mods, jetpacks, elytra etc. all work as long as that mod lets you reach the height. The HUD names only: Ad Astra rocket, Elytra, creative-style flight, Airborne.

## Known limitations (be aware)
- NOT tested in-game by me; it was only compiled.
- Arcana's Y 1500 is hard-coded in Arcana; the config value only changes what the HUD shows.
- Atmospheric protection: not touched. Each mod handles its own.
- Create Aeronautics/Sable vehicles: not supported, left to their original behaviour.
- If Lanterns Additions has already opened its menu, this mod skips its own (configurable).
- Mixin targets Arcana 0.1.72; other Arcana versions may make it silently do nothing.

## 0.1.1 notes
- HUD names: Ad Astra rocket, Elytra, Jetpack (any chestplate item with "jetpack" in its id, e.g. Mekanism/Iron Jetpacks/Create Jetpack), Exosuit (Neural Exosuit), Flight ability, Airborne. Lantern rings, Viltrumite and superhero flight show as "Flight ability" or "Airborne" because they work through the normal flight mechanics; they are not individually named.
- Protection line is display only and never grants anything. It reports: Create: Lanterns ring life support (uses the ring mod's own `LifeSupport.canBreatheInSpace`, i.e. ring on and powered), a full Ad Astra suit with or without oxygen, an incomplete suit, or none.
- Searched and found NO space/oxygen protection in: Viltrumite Core/Flight, Integrated Superheroes, Neural Exosuit (its breathing module is underwater only), Mekanism, Iron Jetpacks, Create Jetpack. Marvel's Iron Man suit only hooks the Stellaris mod, which is not installed. Arcana has no breathing requirement that I found. So the HUD says "none" for those.

## 0.1.3 protection detection
The server works out the player's TOTAL active protection regardless of how they fly, and shows it on the HUD (read-only; nothing is granted or consumed). Order: Lantern ring life support, Ad Astra: Giselle Addon oxygen proof (covers MekaSuit space breathing module and oxygen cans), full Ad Astra suit with/without oxygen, incomplete suit, none.
Caveat: Giselle Addon only switches its proof on once you are somewhere without air, so on the ground or low in the sky the line can say "none active right now" even when you wear the module. Flying with a jetpack while wearing protective armour is detected by the armour, not the jetpack.
Not implemented: Viltrumite breath-holding / vacuum resistance (proposed, needs a design decision; see below).

## 0.1.4 space protection (what exists, verified against the installed jars)
Config: `config/universalspacenav-common.toml` > `[protection]`. Every integration has its own switch, all on by default.

**Viltrumite** (`protection.viltrumite`): players who have actually become Viltrumite (Viltrumite Core `isViltrumite` AND `hasChosenRace`; the Integrated Superheroes Omni-Man serum sets the same flags, so it is covered). 30 minute oxygen reserve (configurable), refilled 10x faster than it drains whenever they stand in breathable air. Vacuum resistance (skips Arcana's vacuum-exposure freezing) and extreme-cold resistance (cancels Ad Astra's cold effect) are separate switches and use no reserve.
**Marvel Superheroes suits** (`protection.marvelSuits`): a complete suit from the configured item tags (default `marvel:iron_man_mark_39_armor`, the one suit family Marvel itself treats as its space suit), helmet closed, every piece holding energy. Extra energy cost per second is configurable and defaults to 0, because Marvel's own costs are never changed.
**Works in both**: Arcana space (hooks Arcana's `protectedAir`, which Arcana uses for both breathing and freezing) and Ad Astra space (hooks Ad Astra's oxygen and cold events).
**No double use / no unintended immunity**: our hooks run only after every earlier protection (Arcana's own, suits, Lantern ring, Giselle Addon) has said "no air"; the hooks only record a use; reserve and energy change once per 5 ticks in one place.
**HUD**: oxygen reserve, suit energy and the list of active protection sources.

**Checked and NOT implemented**
- Integrated Superheroes other powers (Binary, Hulk, Iceman, Storm, Human Torch, Magneto, Colossus, Thing, Scarlet Witch, ...): the mod's code and text describe no space, vacuum, breathing or cold-immunity ability for any of them, so I did not invent one.
- Neural Exosuit: its modules are a fixed built-in list (the "Respiration" module is underwater only) and it exposes no way to add a module or to ask which modules are installed. A dedicated life-support upgrade would mean patching its jar, which you said not to do. Not built.
- Marvel suits other than Mark 39: add their tag to `suitTags` if you want them.
- Viltrumite Core has its own built-in rule that opens Ad Astra's planet list above its own `spaceLimitY` height, regardless of the Arcana/Ad Astra choice. This mod does not change that.
