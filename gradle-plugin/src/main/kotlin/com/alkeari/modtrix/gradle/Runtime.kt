package com.alkeari.modtrix.gradle

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.bundling.Jar
import org.gradle.work.DisableCachingByDefault

internal const val RUNTIME_PACKAGE = "com.alkeari.modtrix"
internal const val EXTRACT_RUNTIME = "extractModtrixRuntime"

/** The runtime's own packages; a mod's classes are never among them, whatever its group. */
internal val RUNTIME_SUBPACKAGES = listOf("api", "impl", "fabric", "forge", "neoforge")

/** Where a mod's copy of the runtime lives once relocated: beside its locator, under its own group and id. */
internal fun Project.runtimePackage(): String = "${mod("group")}.${mod("id")}.modtrix"

internal fun Project.runtimeEnabled(): Boolean = rootProject.findProperty("modtrix.runtime")?.toString()?.toBoolean() ?: true

/**
 * Picks the runtime files for one side and Minecraft version. Entries are `<side>/<from-version>/<path>`; for each path
 * the variant with the newest from-version at or below [mc] wins, so a later variant overrides only the files it carries.
 */
internal fun selectRuntime(entries: Collection<String>, side: String, mc: String): Map<String, String> {
    val chosen = mutableMapOf<String, Pair<String, String>>()
    entries.filter { it.startsWith("$side/") }.forEach { entry ->
        val rest = entry.removePrefix("$side/")
        val from = rest.substringBefore('/')
        val path = rest.substringAfter('/')
        if (path.isEmpty() || compareVersions(from, mc) > 0) return@forEach
        val current = chosen[path]
        if (current == null || compareVersions(from, current.first) > 0) chosen[path] = from to entry
    }
    return chosen.mapValues { it.value.second }
}

/**
 * Mojang's renames, and Fabric API's that came with them, by the Minecraft version that made them. Runtime sources use the
 * names of the oldest version they serve, and a node at or past a rename gets them rewritten, so one source file serves
 * both sides of a rename that changes nothing else.
 */
internal val MOJANG_RENAMES: List<Pair<String, List<Pair<Regex, String>>>> = listOf(
    "1.21.11" to listOf(
        Regex("""\bResourceLocation\b""") to "Identifier",
        Regex("""\.location\(\)""") to ".identifier()",
    ),
    // GUI drawing became render-state extraction; PayloadTypeRegistry took the clientbound and serverbound names.
    "26.1" to listOf(
        Regex("""\bvoid render\(GuiGraphics\b""") to "void extractRenderState(GuiGraphics",
        Regex("""\bsuper\.render\(""") to "super.extractRenderState(",
        Regex("""\bGuiGraphics\b""") to "GuiGraphicsExtractor",
        Regex("""\.drawCenteredString\(""") to ".centeredText(",
        Regex("""\.drawString\(""") to ".text(",
        Regex("""\bPayloadTypeRegistry\.playS2C\(\)""") to "PayloadTypeRegistry.clientboundPlay()",
        Regex("""\bPayloadTypeRegistry\.playC2S\(\)""") to "PayloadTypeRegistry.serverboundPlay()",
    ),
    // Minecraft handed screen management to its Gui.
    "26.2" to listOf(
        Regex("""\bminecraft\.setScreen\(""") to "minecraft.gui.setScreen(",
    ),
)

internal fun applyMojangRenames(source: String, mc: String): String =
    MOJANG_RENAMES.filter { compareVersions(it.first, mc) <= 0 }.flatMap { it.second }
        .fold(source) { text, (pattern, replacement) -> pattern.replace(text, replacement) }

internal fun embeddedRuntime(): ByteArray =
    requireNotNull(ExtractRuntime::class.java.getResourceAsStream("runtime.zip")) { "The Modtrix plugin jar is missing its runtime" }.use { it.readBytes() }

/**
 * The Minecraft versions where the runtime a loader's nodes carry changes: each variant folder of the common and loader
 * sides, and each of Mojang's renames.
 */
internal fun runtimeBoundaries(entries: Collection<String>, loader: Loader): List<String> =
    (entries.filter { it.startsWith("common/") || it.startsWith("${loader.id}/") }.map { it.substringAfter('/').substringBefore('/') } +
        MOJANG_RENAMES.map { it.first }).filter { it.isNotEmpty() }.distinct()

internal fun embeddedRuntimeEntries(): List<String> =
    ZipInputStream(embeddedRuntime().inputStream()).use { zip -> generateSequence { zip.nextEntry }.map { it.name }.toList() }

internal fun ByteArray.sha256(): String = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

@DisableCachingByDefault(because = "Unpacking bundled sources is faster than fetching them from a cache")
abstract class ExtractRuntime : DefaultTask() {
    @get:Input
    abstract val side: Property<String>

    @get:Input
    abstract val minecraftVersion: Property<String>

    /** Changes whenever the embedded runtime does, so an upgraded plugin extracts again. */
    @get:Input
    abstract val runtimeDigest: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(embeddedRuntime().inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { files[it.name] = zip.readBytes() }
        }
        val out = outputDir.get().asFile
        out.deleteRecursively()
        selectRuntime(files.keys, side.get(), minecraftVersion.get()).forEach { (path, entry) ->
            val bytes = files.getValue(entry)
            val text = if (path.endsWith(".java")) applyMojangRenames(bytes.toString(Charsets.UTF_8), minecraftVersion.get()).toByteArray() else bytes
            File(out, path).apply { parentFile.mkdirs() }.writeBytes(text)
        }
    }
}

/** Adds the runtime sources for [side] to this node; a loader node gets the common half through the common sources. */
internal fun Project.addRuntime(side: String) {
    if (!runtimeEnabled()) return
    val digest = embeddedRuntime().sha256()
    val extract = tasks.register(EXTRACT_RUNTIME, ExtractRuntime::class.java) {
        this.side.set(side)
        minecraftVersion.set(mc)
        runtimeDigest.set(digest)
        outputDir.set(layout.buildDirectory.dir("modtrix/runtime"))
    }
    val main = extensions.getByType(SourceSetContainer::class.java).getByName("main")
    main.java.srcDir(extract.map { it.outputDir.dir("java") })
    main.resources.srcDir(extract.map { it.outputDir.dir("resources") })
    addRuntimeStubs(side, digest)
}

/**
 * Compile-time stand-ins for APIs another mod provides at runtime (Mod Menu's on Fabric), in their own source set so they
 * are on the compiler's classpath and never in the jar.
 */
private fun Project.addRuntimeStubs(side: String, digest: String) {
    val stubSide = "stubs-$side"
    val hasStubs = ZipInputStream(embeddedRuntime().inputStream()).use { zip ->
        generateSequence { zip.nextEntry }.any { it.name.startsWith("$stubSide/") }
    }
    if (!hasStubs) return
    val extract = tasks.register("extractModtrixStubs", ExtractRuntime::class.java) {
        this.side.set(stubSide)
        minecraftVersion.set(mc)
        runtimeDigest.set(digest)
        outputDir.set(layout.buildDirectory.dir("modtrix/stubs"))
    }
    val sourceSets = extensions.getByType(SourceSetContainer::class.java)
    val main = sourceSets.getByName("main")
    val stubs = sourceSets.create("modtrixStubs") { java.srcDir(extract.map { it.outputDir.dir("java") }) }
    tasks.named(stubs.compileJavaTaskName, JavaCompile::class.java) { classpath = main.compileClasspath }
    tasks.named(main.compileJavaTaskName, JavaCompile::class.java) { classpath += stubs.output }
}

/** Moves the runtime classes in this node's jar into the mod's own package, before any remapping reads the jar. */
internal fun Project.relocateRuntimeInJar() {
    if (!runtimeEnabled()) return
    val packages = RUNTIME_SUBPACKAGES.associate { "$RUNTIME_PACKAGE.$it" to "${runtimePackage()}.$it" }
    tasks.named("jar", Jar::class.java) {
        inputs.property("modtrixRuntimePackages", packages.toString())
        doLast {
            val file = archiveFile.get().asFile
            val entries = file.inputStream().use { relocate(it, packages, keepManifest = true) }
            writeJar(file, entries)
        }
    }
}
