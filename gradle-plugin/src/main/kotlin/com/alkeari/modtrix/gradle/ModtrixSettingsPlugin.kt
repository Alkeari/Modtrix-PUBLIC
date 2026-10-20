package com.alkeari.modtrix.gradle

import dev.kikugie.stonecutter.settings.StonecutterSettingsExtension
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings

/**
 * Builds the version tree from `targets.<loader>` in the root `gradle.properties`: one node per Minecraft version under
 * `versions/`, and one per loader and version under each loader folder.
 */
class ModtrixSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val targets = Loader.entries.associateWith { loader ->
            settings.providers.gradleProperty("targets.${loader.id}").orNull.orEmpty()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }.filterValues { it.isNotEmpty() }
        if (MODTRIX_INIT in settings.startParameter.taskNames) {
            require(targets.isEmpty()) { "This project already lists targets.<loader> in gradle.properties; $MODTRIX_INIT starts a new one" }
            val identity = ModIdentity.of(
                settings.rootDir.name,
                settings.providers.gradleProperty("mod.id").orNull,
                settings.providers.gradleProperty("mod.name").orNull,
                settings.providers.gradleProperty("mod.group").orNull,
            )
            settings.gradle.rootProject {
                tasks.register(MODTRIX_INIT, ModtrixInit::class.java) {
                    group = "modtrix"
                    description = "Writes a new mod project from the Modtrix starter."
                    projectDir.set(layout.projectDirectory)
                    modId.set(identity.id)
                    modName.set(identity.name)
                    modGroup.set(identity.group)
                    className.set(identity.className)
                }
            }
            return
        }
        require(targets.isNotEmpty()) {
            "Set targets.<loader> in gradle.properties, such as targets.fabric=1.20.1, 1.21.1, or run gradlew $MODTRIX_INIT to start a new mod"
        }
        settings.pluginManager.apply("dev.kikugie.stonecutter")
        // ForgeGradle 7 creates its Eclipse run tasks against a Java 8 toolchain, so without a resolver a machine with no
        // Java 8 fails every task listing and IDE import that realizes them.
        settings.pluginManager.apply("org.gradle.toolchains.foojay-resolver-convention")
        lockPins(settings.rootDir, targets)

        settings.extensions.configure(StonecutterSettingsExtension::class.java) {
            kotlinController.set(true)
            centralScript.set("build.gradle.kts")
            create(settings.rootProject) {
                versions(*targets.values.flatten().distinct().toTypedArray())
                targets.forEach { (loader, loaderVersions) ->
                    branch(loader.id) { versions(*loaderVersions.toTypedArray()) }
                }
            }
        }
        settings.gradle.rootProject { pluginManager.apply(ModtrixControllerPlugin::class.java) }
    }
}
