package com.alkeari.modtrix.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal const val GENERATE_METADATA = "generateModtrixMetadata"

/** The loader metadata file a node needs, by resource path. */
internal fun metadataPath(loader: Loader): String? = when (loader) {
    Loader.FABRIC -> "fabric.mod.json"
    Loader.FORGE -> "META-INF/mods.toml"
    Loader.NEOFORGE -> "META-INF/neoforge.mods.toml"
    Loader.COMMON -> null
}

/** The class Fabric Loader starts: `mod.entrypoint_fabric`, or `<group>.<id>.fabric.<archives name>Fabric` by convention. */
internal fun Project.fabricEntrypoint(): String =
    rootProject.findProperty("mod.entrypoint_fabric")?.toString()?.takeIf { it.isNotBlank() }
        ?: "${mod("group")}.${mod("id")}.fabric.${mod("archives_name")}Fabric"

/**
 * Default metadata, still holding the template names `processResources` expands, so a generated file and a hand-written
 * one take the same path through the build. A mod that needs more (extra dependencies, a client entrypoint) writes its
 * own file and this one is not generated.
 */
internal fun metadataTemplate(loader: Loader, fabricEntrypoint: String, mixins: List<String>): String = when (loader) {
    Loader.FABRIC -> buildString {
        appendLine("{")
        appendLine("  \"schemaVersion\": 1,")
        appendLine("  \"id\": \"\${modId}\",")
        appendLine("  \"version\": \"\${modVersion}\",")
        appendLine("  \"name\": \"\${modName}\",")
        appendLine("  \"description\": \"\${modDescription}\",")
        appendLine("  \"authors\": [\"\${modAuthor}\"],")
        appendLine("  \"license\": \"\${modLicense}\",")
        appendLine("  \"environment\": \"*\",")
        appendLine("  \"entrypoints\": {")
        appendLine("    \"main\": [\"$fabricEntrypoint\"]")
        appendLine("  },")
        if (mixins.isNotEmpty()) appendLine("  \"mixins\": [${mixins.joinToString(", ") { "\"$it\"" }}],")
        appendLine("  \"depends\": {")
        appendLine("    \"fabricloader\": \">=\${depsFabricLoader}\",")
        appendLine("    \"fabric-api\": \"*\",")
        appendLine("    \"minecraft\": \"\${minecraftRange}\"")
        appendLine("  }")
        appendLine("}")
    }
    Loader.FORGE, Loader.NEOFORGE -> {
        val dependency = if (loader == Loader.FORGE) "mandatory = true" else "type = \"required\""
        buildString {
            appendLine("modLoader = \"javafml\"")
            // Forge's language loader reports only Forge's major version (47 on 47.4.20); NeoForge's follows FML, which started again at 1.
            appendLine(if (loader == Loader.FORGE) "loaderVersion = \"\${forgeLanguageRange}\"" else "loaderVersion = \"[1,)\"")
            appendLine("license = \"\${modLicense}\"")
            appendLine()
            appendLine("[[mods]]")
            appendLine("modId = \"\${modId}\"")
            appendLine("version = \"\${modVersion}\"")
            appendLine("displayName = \"\${modName}\"")
            appendLine("authors = \"\${modAuthor}\"")
            appendLine("description = \"\${modDescription}\"")
            for ((id, range) in listOf(loader.id to "\${loaderRange}", "minecraft" to "\${minecraftRange}")) {
                appendLine()
                appendLine("[[dependencies.\${modId}]]")
                appendLine("modId = \"$id\"")
                appendLine(dependency)
                appendLine("versionRange = \"$range\"")
                appendLine("ordering = \"NONE\"")
                appendLine("side = \"BOTH\"")
            }
            // NeoForge reads mixin configs from its metadata, never from the MixinConfigs manifest entry Forge uses.
            if (loader == Loader.NEOFORGE) {
                for (config in mixins) {
                    appendLine()
                    appendLine("[[mixins]]")
                    appendLine("config = \"$config\"")
                }
            }
        }
    }
    Loader.COMMON -> error("The common node has no loader metadata")
}

@DisableCachingByDefault(because = "Writing one small text file is faster than fetching it from a cache")
abstract class GenerateMetadata : DefaultTask() {
    /** Resource path to file text; empty when the mod wrote its own. */
    @get:Input
    abstract val files: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        files.get().forEach { (path, text) -> File(out, path).apply { parentFile.mkdirs() }.writeText(text) }
    }
}

/** Generates this node's loader metadata from `mod.*` unless its own or the common resources already carry the file. */
internal fun Project.addDefaultMetadata(loader: Loader) {
    val path = metadataPath(loader) ?: return
    val own = resourceDirs(loader).map { it.resolve(path) }
    val text = if (own.any { it.isFile }) null else metadataTemplate(loader, fabricEntrypoint(), mixinConfigs(loader))
    val generate = tasks.register(GENERATE_METADATA, GenerateMetadata::class.java) {
        files.set(if (text == null) emptyMap() else mapOf(path to text))
        outputDir.set(layout.buildDirectory.dir("modtrix/metadata"))
    }
    extensions.getByType(SourceSetContainer::class.java).getByName("main").resources.srcDir(generate.map { it.outputDir })
}
