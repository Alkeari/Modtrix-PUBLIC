package com.alkeari.modtrix.gradle

import java.security.MessageDigest
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.CopySpec
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar

/**
 * Packages the shipped jars into `build/release`: one per loader spanning every Minecraft version it builds, and one
 * universal jar for every loader. Fabric Loader picks the nested build whose Minecraft range matches; on Forge and
 * NeoForge the relocated locator does.
 */
class ModtrixControllerPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.gradle.projectsEvaluated { project.registerReleaseTasks() }
    }
}

/** The task whose jar ships: Loom remaps Fabric jars until Minecraft ships unobfuscated (26.1), and Forge reobfuscates until it runs on Mojang's names (1.20.6). */
private fun shippedJarTask(loader: Loader, mc: String) = when (loader) {
    Loader.FABRIC -> if (compareVersions(mc, "26.1") >= 0) "jar" else "remapJar"
    Loader.FORGE -> if (compareVersions(mc, "1.20.6") < 0) "reobfJar" else "jar"
    else -> "jar"
}

/** The shipped jar file of a node, whether its task is an archive task or the SRG rename. */
private fun Project.shippedJar(loader: Loader, mc: String): Provider<RegularFile> {
    val task = project(":${loader.id}:$mc").tasks.named(shippedJarTask(loader, mc))
    return task.flatMap { if (it is RenameJar) it.outputJar else (it as AbstractArchiveTask).archiveFile }
}
private val LOCATOR_SERVICES = mapOf(
    Loader.FORGE to "META-INF/services/net.minecraftforge.forgespi.locating.IDependencyLocator",
    Loader.NEOFORGE to "META-INF/services/net.neoforged.neoforgespi.locating.IDependencyLocator",
)

private fun Project.registerReleaseTasks() {
    val modId = mod("id")
    val modVersion = mod("version")
    val archivesName = mod("archives_name")
    val releaseDir = layout.buildDirectory.dir("release")
    val locatorPackage = locatorPackage()
    val nestedDir = "META-INF/$locatorPackage"
    val icon = file("common/src/main/resources/icon.png")

    fun versionJar(loader: Loader, mc: String) =
        project(":${loader.id}:$mc").tasks.named(shippedJarTask(loader, mc), AbstractArchiveTask::class.java)

    val shipped = mutableListOf<TaskProvider<out Task>>()
    var fabricBundle: TaskProvider<Jar>? = null

    val fabricTargets = targets(Loader.FABRIC)
    if (fabricTargets.isNotEmpty()) {
        val jars = fabricTargets.map { versionJar(Loader.FABRIC, it) }
        val loaderFloor = fabricTargets.map { versionPins(it).getValue("deps.fabric_loader") }.minWith(::compareVersions)
        val json = fabricBundleJson(
            modId = modId, modName = mod("name"), modVersion = modVersion, author = mod("author"), license = mod("license"),
            hasIcon = icon.isFile, loaderFloor = loaderFloor, jarNames = jars.map { it.get().archiveFileName.get() },
        )
        val metadataFile = layout.buildDirectory.file("modtrix/fabric/fabric.mod.json")
        val metadata = tasks.register("fabricBundleMetadata") {
            inputs.property("json", json)
            outputs.file(metadataFile)
            doLast { metadataFile.get().asFile.apply { parentFile.mkdirs() }.writeText(json) }
        }
        val bundle = tasks.register("fabricBundle", Jar::class.java) {
            group = "build"
            archiveFileName.set("$archivesName-$modVersion-FABRIC.jar")
            destinationDirectory.set(releaseDir.map { it.dir("fabric") })
            from(jars.map { provider -> provider.flatMap { it.archiveFile } }) { into("META-INF/jars") }
            from(metadata)
            if (icon.isFile) from(icon)
        }
        fabricBundle = bundle
        shipped += bundle
    }

    val digest = MessageDigest.getInstance("SHA-256").digest(embeddedLocator()).joinToString("") { "%02x".format(it) }
    val locatorJar = tasks.register("relocateLocator", RelocateLocator::class.java) {
        targetPackage.set(locatorPackage)
        locatorDigest.set(digest)
        outputJar.set(layout.buildDirectory.file("modtrix/locator.jar"))
    }

    val locatorLoaders = listOf(Loader.FORGE, Loader.NEOFORGE).filter { targets(it).isNotEmpty() }
    val indexes = locatorLoaders.associateWith { loader ->
        val lines = targets(loader).map { mc ->
            val range = requireNotNull(versionPins(mc)["minecraft_range_${loader.id}"]) { "Missing 'minecraft_range_${loader.id}' in versions/$mc/gradle.properties" }
            "${loader.id}-$mc.jar $range"
        }
        val indexFile = layout.buildDirectory.file("modtrix/nested/${loader.id}.txt")
        tasks.register("${loader.id}NestedIndex") {
            inputs.property("lines", lines)
            outputs.file(indexFile)
            doLast { indexFile.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n")) }
        }
    }

    fun CopySpec.nested(loader: Loader) {
        targets(loader).forEach { mc ->
            from(shippedJar(loader, mc)) {
                into(nestedDir)
                rename { "${loader.id}-$mc.jar" }
            }
        }
        from(indexes.getValue(loader)) { into(nestedDir) }
    }

    locatorLoaders.forEach { loader ->
        shipped += tasks.register("${loader.id}Jar", Jar::class.java) {
            group = "build"
            archiveFileName.set("$archivesName-$modVersion-${loader.label}.jar")
            destinationDirectory.set(releaseDir.map { it.dir(loader.id) })
            from(locatorJar.map { zipTree(it.outputJar) }) { exclude(LOCATOR_SERVICES.filterKeys { it != loader }.values) }
            nested(loader)
            manifest.attributes(mapOf("Automatic-Module-Name" to locatorPackage))
        }
    }

    if (listOfNotNull(fabricBundle).size + locatorLoaders.size > 1) {
        val bundle = fabricBundle
        shipped += tasks.register("universalJar", Jar::class.java) {
            group = "build"
            archiveFileName.set("$archivesName-$modVersion.jar")
            destinationDirectory.set(releaseDir.map { it.dir("universal") })
            if (bundle != null) from(bundle.map { zipTree(it.archiveFile) }) { exclude("META-INF/MANIFEST.MF") }
            if (locatorLoaders.isNotEmpty()) {
                from(locatorJar.map { zipTree(it.outputJar) }) { exclude(LOCATOR_SERVICES.filterKeys { it !in locatorLoaders }.values) }
            }
            locatorLoaders.forEach { nested(it) }
            manifest.attributes(mapOf("Automatic-Module-Name" to locatorPackage))
        }
    }

    tasks.register("release") {
        group = "build"
        description = "Builds every shipped jar into build/release: one per loader, and the all-loader jar under universal."
        dependsOn(shipped)
    }
}

internal fun fabricBundleJson(
    modId: String,
    modName: String,
    modVersion: String,
    author: String,
    license: String,
    hasIcon: Boolean,
    loaderFloor: String,
    jarNames: List<String>,
): String {
    fun quote(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    val jars = jarNames.joinToString(",\n") { "    { \"file\": ${quote("META-INF/jars/$it")} }" }
    val icon = if (hasIcon) "\n  \"icon\": \"icon.png\"," else ""
    return """
        |{
        |  "schemaVersion": 1,
        |  "id": ${quote("${modId}bundle")},
        |  "version": ${quote(modVersion)},
        |  "name": ${quote("$modName Bundle")},
        |  "description": ${quote("Carries $modName for every supported Minecraft version; Fabric Loader loads the one that matches the game.")},
        |  "authors": [${quote(author)}],
        |  "license": ${quote(license)},$icon
        |  "environment": "*",
        |  "jars": [
        |$jars
        |  ],
        |  "depends": {
        |    "fabricloader": ${quote(">=$loaderFloor")}
        |  },
        |  "custom": {
        |    "modmenu": {
        |      "parent": ${quote(modId)}
        |    }
        |  }
        |}
        |""".trimMargin()
}

/** Numeric comparison of dotted versions, ignoring anything after a `+` or `-`. */
internal fun compareVersions(a: String, b: String): Int {
    fun parts(v: String) = v.substringBefore('+').substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val x = parts(a)
    val y = parts(b)
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
