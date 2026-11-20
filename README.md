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
