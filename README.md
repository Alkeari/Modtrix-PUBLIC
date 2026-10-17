# Modtrix

A Gradle plugin and runtime library for Minecraft mod authors: write a mod once and build it for Fabric, Forge and NeoForge across several Minecraft versions.

## What It Does

- **One source tree, many targets** - a mod lists the Minecraft versions it builds for each loader, and Modtrix builds every one of them from the same sources, with Stonecutter comments for the lines that differ between versions.
- **One jar per loader** - each loader's jar carries a build for every Minecraft version it supports and loads the one that matches the game; an all-loader jar carries them all.
- **Runtime API** - loader detection, commands, server and client ticks, player join, messages in both directions, registration and settings (one `config/<modid>.toml` and a settings screen opened from Mod Menu on Fabric and the Config button on Forge and NeoForge), the same calls on Fabric, Forge and NeoForge, bundled inside each mod so players install nothing extra.

## Requirements

- Gradle 9.7.1 or newer (the starter project carries its wrapper).
- A JDK at least as new as the newest Minecraft version you target needs: 17 through 1.20.4, 21 from 1.20.5, 25 from 26.1.

## Installation

Start a new mod: in an empty folder holding the Gradle wrapper and this `settings.gradle.kts`, run `gradlew modtrixInit -Pmod.name="My Cool Mod" -Pmod.group=com.yourname`.

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.alkeari.com")
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("com.alkeari.modtrix") version "1.0.0"
}
```

It writes a working project: identity in `gradle.properties`, one entry class per loader, and `targets.<loader>` listing the Minecraft versions each loader builds. Then:

1. Run `gradlew release`. The jars land in `build/release/`: one per loader, and one universal jar that runs on all three.
2. Commit the `versions/<version>/gradle.properties` files the first build wrote. They pin Fabric Loader, Fabric API, Forge, NeoForge and Parchment for each version until you change them.

Modtrix is published to maven.alkeari.com. Keep the Fabric Maven in the list, since Fabric Loom is published only there. The starter project, with its wrapper and these settings, is attached to each release on the [GitHub repository](https://github.com/Alkeari/Modtrix-PUBLIC/releases).

## Configuration

Everything is set in your mod's `gradle.properties`:

- `mod.id`, `mod.name`, `mod.group` and the other `mod.*` keys: your mod's identity, used to generate `fabric.mod.json`, `mods.toml` and `neoforge.mods.toml`. Write your own file in a loader's `src/main/resources` to use it instead.
- `targets.fabric`, `targets.forge`, `targets.neoforge`: the Minecraft versions each loader builds.
- `modtrix.runtime=false`: leaves the runtime library out of your jars.

## Compatibility

Builds mods for Minecraft 1.20.1 through 26.3: Fabric on every version, Forge and NeoForge on every version each loader publishes a build for.

## Support

Source, releases and issues: [Alkeari/Modtrix-PUBLIC](https://github.com/Alkeari/Modtrix-PUBLIC).

## License

Modtrix is licensed under the [GNU Lesser General Public License v3.0](https://www.gnu.org/licenses/lgpl-3.0.html).
