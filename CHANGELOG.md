# Changelog
## 0.1.0
- First version. Per-player Arcana / Ad Astra destination, server-side, saved with the player.
- HUD (destination, flight method, entry altitude, altitude), movable and toggleable in config.
- Arcana's automatic transition skipped for Ad Astra mode players and anyone in an Ad Astra rocket (optional mixin).
- Ad Astra planet menu opened at the configured altitude (default Y 5000) for Ad Astra mode players.

## 0.1.1
- HUD: jetpack/exosuit names, display-only protection line.

## 0.1.2
- Protection line now reads Create: Lanterns ring life support and Ad Astra suit oxygen state (display only).

## 0.1.3
- Protection computed on the server and synced to the HUD; detects Giselle Addon oxygen proof.

## 0.1.4
- Viltrumite oxygen reserve, vacuum and cold resistance; sealed Marvel suit air; Arcana and Ad Astra hooks; HUD oxygen/energy/sources.

## 0.1.5
- Viltrumite Core native menu follows mode; Lanterns menu de-duplication; Exosuit Life-Support Module (Curios); optional superhero power list; persistence safeguards; /usn commands.

## 0.1.6
- Diagnostics for Arcana's space sky (client only, off by default): `/usnsky nodepth` draws the Earth and Moon surfaces without the depth test; `/usnsky dump` logs GL state and depth values around the planet draws. Config: client `[diagnostics] disableSurfaceDepthTest`.

## 0.1.7
- `/usnsky dump` now also reports, per Earth/Moon/atmosphere layer, how many fragments were drawn and how many screen pixels changed, plus the planet texture's size and average colour.

## 0.1.8
- Sky dump also logs the GPU state at each draw inside an Arcana sky layer (vertex counts, scissor, colour mask, program link status, GL errors).

## 0.1.9
- Iris compatibility fix for Arcana's space sky (Earth/Moon invisible with Iris installed): Iris's fully buffered draw queue ignores endBatch(), so Arcana's planets were never drawn at the right time. With Iris present, the sky layers now use their own immediate buffer. Config: client [compat] arcanaSkyOwnBuffer (default true).

## 0.1.10
- Create: Lanterns ring life support and vacuum protection in Arcana's space (it only worked in Ad Astra space before). Costs the ring's own energy, no extra cost. Config: protection.lanternRing.enabled (default true). Not applied in Ad Astra space, so nothing is doubled.

## 0.1.11
- Fix: Arcana Earth/Moon vanishing when a shader pack (e.g. Complementary Reimagined) is on. Iris masks colour/depth writes for shaders it does not know; USN now lets Arcana's planet shader through, only while a planet layer is drawing (config compat.arcanaSkyAllowUnknownShader).
- /usnsky dump now also logs the active shader, Iris pipeline and Iris skip decision per draw.

## 0.1.12
- Diagnostic only, nothing is cleared: LeakWatch watches the previous world/player with weak references and logs which known fields in Arcana, Create: Lanterns and Neural Exosuit still hold them (config diagnostics.leakWatch, default on).
- /usnleak check, /usnleak status, /usnleak gc confirm (opt-in garbage collection).

## 0.1.13
- Diagnostics only: /usnsky skip <0-4|off> leaves one Arcana sky layer out, /usnsky allow <on|off> toggles the Iris override, /usnsky dump now also logs per-layer GL state before/after and the screen area each layer changed.

## 0.1.14
- With a shader pack active, Arcana's atmosphere layer is not drawn (it showed as a cyan wash with stripes over all of Earth). Config compat.arcanaSkyHideAtmosphereWithShaders.

## 0.1.15
- Diagnostic only: /usnleak find searches memory (read-only, background) for what keeps old worlds and players alive and logs the reference path.

## 0.1.16
- Memory leak workaround (config leakFix, default on): releases stale Celestisynth player entries and Arcana MobCasterClient's old world. Only stale entries are touched.

## 0.1.17
- Diagnostic only (no behaviour change): lag watch. Logs "[USN lag]" lines only when something is slow: server ticks over 150 ms, client game-thread stalls over 250 ms, GC time during them, and movement keys held without the player moving (says whether input, a freeze, or the server was the cause). `/usnlag status|reset`. Config: lagWatch.

## 0.1.18
- Diagnostic only: movement lag lines now also record velocity, collision, speed, effects, vehicle, blocks under the player and equipped items. Join-time gaps (no player yet) are no longer reported as freezes.

## 0.1.19
- Diagnostic only: `/usnleak find` no longer follows weak references or weak-map keys (they do not keep objects alive), so it only reports real holders.

## 0.1.20
- Diagnostic only: movement lag lines are now labelled WALKING / SWIMMING / FLYING / AIRBORNE and only measure HORIZONTAL movement (vertical flight speed no longer counts as "moved" or "not moved"). `/usnlag status` shows slow vs. normal starts per state.

## 0.1.21
- Join-time fix: while JEI Stuff scans villager trades at world join, map trades (Wan Ancient Beasts, Alex's Caves, Arcana) no longer run a slow structure search per test. Only active when JEI Stuff is on the call stack; real trades are untouched. Works on a dedicated server too. Config: compat.jeiStuffSkipMapSearch (common config, default on).

## 0.1.22
- Log-flood fix: GeckoLib's "Unable to find animation" error (Creeper Overhaul dripstone creeper, Ars Elemental flashjack) no longer prints a stack trace on every frame. First message is kept, repeats are shown at most once a minute with a count. Config: compat.quietGeckolibMissingAnimation (client, default on). Does not change how mobs look.

## 0.1.23
- Leak fix: Celestisynth has two more player lists (otherAnimationData, mirroredAnimationData) that kept old worlds alive; stale entries are now released too.
- /usnleak find no longer reports weak maps wrapped by synchronizedMap (Fauna Engine false alarm).

## 0.1.24
- /usnleak find searches deeper (3.5 million objects) so it can trace the last old worlds. Diagnostic only.

## 0.1.25
- /usnleak find: runs a full memory cleanup first (so objects that were just not collected yet are not reported), then searches much deeper and also through threads, thread-local data, lambdas and network tasks. Stops early if memory gets low. Diagnostic only.

## 0.1.26
- Leak fix: the Morph mod kept the previous (old) player and world alive in its hand renderer; that stale reference is now released.

## 0.1.27
- Crash fix: Iron's Lib player statues no longer crash the game when GeckoLib rejected their pose animation file at startup; the statue is drawn in its default pose and one warning is logged.

## 0.1.28
- Server-join crash fix: joining a dedicated server disconnected with "Network Protocol Error" because Pastel asked Apothic Enchanting for an enchantment level before enchantments had loaded while the creative tabs were being built. That one early question now answers 0 and the login finishes.

## 0.1.29
- Server fix: Iron Jetpacks' creative tab and JEI list missed every jetpack on a dedicated server because the server's jetpack list arrived after the game built the tabs. The creative tabs are now rebuilt once when the list arrives.

## 0.1.30
- Creative tabs: the 0.1.29 rebuild was skipped by the game/ModernFix cache ("nothing changed"). Now forces a real rebuild (twice with different permission flags), refreshes the creative search box, and repeats once ~5 s after joining any world so late server data (Iron Jetpacks, Gateways to Eternity, ...) shows up.

## 0.1.31
- Crash fix: Marvel Legacy drew your bracelet Curios slot in first person by casting it to its own type, so wearing another mod's bracelet (Reliquified Artifacts' Onion Ring) crashed the game (ClassCastException in PlayerRenderer). It now sees an empty slot for bracelets that are not its own. Config: compat.marvelLegacyBraceletFix.

## 0.1.32
- Client fix: when the server sends one hidden mob value of the wrong type for its slot ("Invalid entity data item type for field 15"), skip that value and log it instead of disconnecting. Config: compat.entityDataMismatchFix. Client only.
