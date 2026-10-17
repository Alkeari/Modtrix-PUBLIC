package com.alkeari.modtrix.gradle

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

/** Data pack format per Minecraft version; a version newer than this table needs `pack_format` written by hand. */
internal val PACK_FORMATS = mapOf(
    "1.20.1" to 15, "1.20.2" to 18, "1.20.3" to 26, "1.20.4" to 26, "1.20.5" to 41, "1.20.6" to 41, "1.21" to 48,
    "1.21.1" to 48, "1.21.2" to 57, "1.21.3" to 57, "1.21.4" to 61, "1.21.5" to 71, "1.21.6" to 80, "1.21.7" to 81,
    "1.21.8" to 81, "1.21.9" to 88, "1.21.10" to 88, "1.21.11" to 94, "26.1" to 101, "26.1.1" to 101, "26.1.2" to 101,
    "26.2" to 107, "26.3" to 121,
)

/** NeoForge versions that publish no ModDevGradle bundle, so their nodes build against NeoForm instead. */
internal val NEOFORM_VERSIONS = setOf("1.20.2", "1.20.3", "1.20.5")

internal fun javaVersionFor(mc: String): Int = when {
    compareVersions(mc, "1.20.5") < 0 -> 17
    compareVersions(mc, "26.1") < 0 -> 21
    else -> 25
}

/** The prefix NeoForge versions for [mc] start with: `21.1.` for 1.21.1, `21.0.` for 1.21, `26.1.0.` for 26.1. */
internal fun neoforgePrefix(mc: String): String {
    val parts = mc.split('.')
    return if (parts.first() == "1") "${parts[1]}.${parts.getOrElse(2) { "0" }}."
    else "${parts[0]}.${parts[1]}.${parts.getOrElse(2) { "0" }}."
}

/** The keys a version's pin file needs for the loaders that target it, in the order they are written. */
internal fun requiredPins(mc: String, loaders: Set<Loader>): List<String> = buildList {
    addAll(listOf("java.version", "minecraft_version", "pack_format", "deps.parchment"))
    // The common node compiles against Fabric Loader too, so a version with no Fabric node still pins it.
    if (Loader.FABRIC in loaders || Loader.COMMON in loaders) add("deps.fabric_loader")
    if (Loader.FABRIC in loaders) addAll(listOf("minecraft_range_fabric", "deps.fabric_api"))
    if (Loader.FORGE in loaders) addAll(listOf("minecraft_range_forge", "deps.forge", "deps.forge_range"))
    if (Loader.NEOFORGE in loaders) {
        addAll(listOf("minecraft_range_neoforge", "deps.neoforge", "deps.neoforge_range"))
        if (mc in NEOFORM_VERSIONS) add("deps.neoform")
    }
}

/** Every `<version>` in a Maven metadata file, oldest first as Maven lists them. */
internal fun mavenVersions(xml: String): List<String> =
    Regex("<version>([^<]+)</version>").findAll(xml).map { it.groupValues[1] }.toList()

/** The newest NeoForge release for [mc], or its newest beta when it has no release. */
internal fun newestNeoForge(versions: List<String>, mc: String): String? {
    val matching = versions.filter { it.startsWith(neoforgePrefix(mc)) }
    val stable = matching.filterNot { it.contains('-') }
    return (stable.ifEmpty { matching }).maxWithOrNull(::compareVersions)
}

/** Fills what a version's pin file is missing and leaves every value already there alone, so a written pin stays locked. */
internal class PinResolver(private val fetch: (String) -> String?) {
    fun resolve(key: String, mc: String): String? = when (key) {
        "java.version" -> javaVersionFor(mc).toString()
        "minecraft_version" -> mc
        "pack_format" -> PACK_FORMATS[mc]?.toString()
        "deps.parchment" -> fetch("https://maven.parchmentmc.org/org/parchmentmc/data/parchment-$mc/maven-metadata.xml")
            ?.let { Regex("<release>([^<]+)</release>").find(it)?.groupValues?.get(1) }.orEmpty()
        "minecraft_range_fabric" -> mc
        "deps.fabric_loader" -> fetch("https://meta.fabricmc.net/v2/versions/loader")
            ?.let { json(it) as List<*> }?.map { it as Map<*, *> }?.firstOrNull { it["stable"] == true }?.get("version")?.toString()
        "deps.fabric_api" -> fetch("https://api.modrinth.com/v2/project/fabric-api/version?game_versions=%5B%22$mc%22%5D&loaders=%5B%22fabric%22%5D")
            ?.let { json(it) as List<*> }?.firstOrNull()?.let { (it as Map<*, *>)["version_number"]?.toString() }
        "minecraft_range_forge", "minecraft_range_neoforge" -> "[$mc]"
        "deps.forge" -> fetch("https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json")
            ?.let { ((json(it) as Map<*, *>)["promos"] as Map<*, *>)["$mc-latest"]?.toString() }
        "deps.neoforge" -> fetch("https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml")
            ?.let { newestNeoForge(mavenVersions(it), mc) }
        "deps.neoform" -> fetch("https://maven.neoforged.net/releases/net/neoforged/neoform/maven-metadata.xml")
            ?.let { xml -> mavenVersions(xml).lastOrNull { it.startsWith("$mc-") } }
        else -> null
    }

    /** Derived from pins already chosen, so they are never fetched. */
    fun derive(key: String, pins: Map<String, String>): String? = when (key) {
        "deps.forge_range" -> pins["deps.forge"]?.let { "[${it.substringBefore('.')},)" }
        "deps.neoforge_range" -> pins["deps.neoforge"]?.let { v ->
            val parts = v.substringBefore('-').split('.')
            "[${parts.dropLast(1).joinToString(".")},)"
        }
        else -> null
    }

    /** Lines to append to [file] so it carries every key in [required]; empty when nothing is missing. */
    fun missingLines(file: File, mc: String, required: List<String>): List<String> {
        val pins = linkedMapOf<String, String>()
        if (file.isFile) Properties().apply { file.reader(Charsets.UTF_8).use(::load) }.forEach { (k, v) -> pins[k.toString()] = v.toString().trim() }
        val lines = mutableListOf<String>()
        for (key in required) {
            if (key in pins) continue
            val value = derive(key, pins) ?: resolve(key, mc)
                ?: error("Modtrix could not resolve '$key' for Minecraft $mc; write it in ${file.path}")
            pins[key] = value
            lines += "$key=$value"
        }
        return lines
    }
}

private fun json(text: String): Any = groovy.json.JsonSlurper().parseText(text)

private val HTTP: HttpClient by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NORMAL).build() }

internal fun httpGet(url: String): String? {
    val request = HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "Modtrix").GET().build()
    val response = HTTP.send(request, HttpResponse.BodyHandlers.ofString())
    return if (response.statusCode() == 200) response.body() else null
}

/** Writes or completes `versions/<mc>/gradle.properties` for every targeted version, before any node reads it. */
internal fun lockPins(rootDir: File, targets: Map<Loader, List<String>>, resolver: PinResolver = PinResolver(::httpGet)) {
    val byVersion = linkedMapOf<String, MutableSet<Loader>>()
    targets.forEach { (loader, versions) -> versions.forEach { byVersion.getOrPut(it) { mutableSetOf() }.add(loader) } }
    byVersion.forEach { (mc, loaders) ->
        val file = File(rootDir, "versions/$mc/gradle.properties")
        val lines = resolver.missingLines(file, mc, requiredPins(mc, loaders))
        if (lines.isEmpty()) return@forEach
        file.parentFile.mkdirs()
        val existing = if (file.isFile) file.readText(Charsets.UTF_8) else ""
        val separator = if (existing.isEmpty() || existing.endsWith("\n")) "" else "\n"
        file.writeText(existing + separator + lines.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }
}
