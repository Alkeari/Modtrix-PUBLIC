# Changelog

What changed in Modtrix, newest first. Only changes a mod author using Modtrix can notice are listed.

## v1.1.0 - 2026-10-19

- Added: `ModConfig.Builder.checkForUpdates(url)` reads the mod's update file once per start and tells the player in chat only when a newer version is out for their Minecraft version and loader, with its own setting, on by default, to turn it off.
- Added: `Updates.check(modId, url)` runs the same update check for a mod that keeps its settings outside `ModConfig`.
- Fixed: A settings screen page never ends on a section heading, and a page that starts partway through a section repeats that section's heading above its settings.
- Fixed: A setting name too long for the settings screen's label column is cut short with an ellipsis instead of running into its button, and its tooltip begins with the full name.
- Fixed: A setting label built from its key keeps short words such as for, of and the lowercase, and a label of six or more words reads as a sentence.
- Fixed: A setting's tooltip states its default as its button shows it (ON or OFF, or the option's name) instead of the raw config value.

## v1.0.0 - 2026-10-17

- Added: One mod source tree builds for Fabric, Forge and NeoForge on every Minecraft version from 1.20.1 to 26.3.
- Added: One jar per loader that loads the build matching the running Minecraft version, and one universal jar for all three loaders.
- Added: `modtrixInit` writes a working starter project, and the first build pins each version's loader and library versions.
- Added: A runtime API for loader detection, commands, ticks, player join, messages, registration and settings with a settings screen, bundled inside each mod.
