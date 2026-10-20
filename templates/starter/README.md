# Modtrix starter

A new mod that builds for Fabric, Forge and NeoForge across several Minecraft versions from one source tree.

In an empty folder holding only the Gradle wrapper and a `settings.gradle.kts` that applies the plugin, `gradlew modtrixInit -Pmod.name="My Cool Mod" -Pmod.group=com.yourname` writes this project with the identity, package and classes already renamed. Otherwise copy this folder and:

1. Set your mod's identity in `gradle.properties` (`mod.id`, `mod.name`, `mod.group` and the rest) and rename the
   `com.example.mymod` package and the `MyMod` classes to match.
2. List the Minecraft versions each loader should build in `targets.<loader>`.
3. Run `gradlew release`. The first build writes `versions/<version>/gradle.properties` for every version you
   target, with the newest Fabric Loader, Fabric API, Forge, NeoForge and Parchment for it; commit those files, since
   they pin your build until you change them.

The jars land in `build/release/`: one per loader, and one universal jar that runs on all three.

`settings.gradle.kts` gets the plugin from maven.alkeari.com, listed first in its `pluginManagement` repositories.
Keep the Fabric Maven there too, since Fabric Loom is published only on it.

Loader metadata (`fabric.mod.json`, `mods.toml`, `neoforge.mods.toml`) is generated from `gradle.properties`. To
declare more (another dependency, a client entrypoint), write your own file in that loader's `src/main/resources`
and it is used instead.
