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
