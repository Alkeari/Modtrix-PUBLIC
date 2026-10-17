package com.alkeari.modtrix.gradle

import dev.kikugie.stonecutter.build.StonecutterBuildExtension
import java.util.Properties
import org.gradle.api.Project

internal fun Project.mod(key: String): String =
    requireNotNull(rootProject.findProperty("mod.$key")?.toString()?.takeIf { it.isNotEmpty() }) { "Missing 'mod.$key' in gradle.properties" }

internal fun Project.targets(loader: Loader): List<String> =
    rootProject.findProperty("targets.${loader.id}")?.toString().orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** The package each mod's copy of the locator is relocated into. */
internal fun Project.refmapName(): String = "${mod("id")}.refmap.json"

internal fun Project.locatorPackage(): String = "${mod("group")}.${mod("id")}.modtrix.locator"

internal val Project.stonecutterBuild: StonecutterBuildExtension
    get() = extensions.getByType(StonecutterBuildExtension::class.java)

/** The Minecraft version this node builds. */
internal val Project.mc: String
    get() = stonecutterBuild.current.project

/** Everything in `versions/<mc>/gradle.properties`, the pins for one Minecraft version. */
internal fun Project.versionPins(mc: String): Map<String, String> {
    val file = rootProject.file("versions/$mc/gradle.properties")
    require(file.isFile) { "Missing ${file.path}" }
    val properties = Properties()
    file.reader(Charsets.UTF_8).use(properties::load)
    return properties.stringPropertyNames().associateWith { properties.getProperty(it).trim() }
}

internal fun Project.pin(key: String): String =
    requireNotNull(versionPins(mc)[key]?.takeIf { it.isNotEmpty() }) { "Missing '$key' in versions/$mc/gradle.properties" }

internal fun Project.optionalPin(key: String): String? = versionPins(mc)[key]?.takeIf { it.isNotEmpty() }

/** `deps.cloth_config` becomes `depsClothConfig`, the name a metadata template uses. */
internal fun templateName(key: String): String {
    val words = key.split('.', '_', '-').filter { it.isNotEmpty() }
    return words.first() + words.drop(1).joinToString("") { word -> word.replaceFirstChar { it.uppercaseChar() } }
}
