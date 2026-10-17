# Changelog

What changed in Modtrix, newest first. Only changes a mod author using Modtrix can notice are listed.

## v1.0.0 - 2026-10-17

- Added: One mod source tree builds for Fabric, Forge and NeoForge on every Minecraft version from 1.20.1 to 26.3.
- Added: One jar per loader that loads the build matching the running Minecraft version, and one universal jar for all three loaders.
- Added: `modtrixInit` writes a working starter project, and the first build pins each version's loader and library versions.
- Added: A runtime API for loader detection, commands, ticks, player join, messages, registration and settings with a settings screen, bundled inside each mod.
