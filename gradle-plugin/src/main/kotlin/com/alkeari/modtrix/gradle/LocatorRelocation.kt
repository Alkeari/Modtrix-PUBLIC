package com.alkeari.modtrix.gradle

import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

internal const val LOCATOR_PACKAGE = "com.alkeari.modtrix.locator"

/**
 * Moves every class under each key package of [packages] into its value package and rewrites the service files that
 * name them, so each mod carries its own copy and two mods built on different Modtrix versions never share a class.
 * The manifest is dropped unless [keepManifest], since a nested locator jar must not carry the outer jar's attributes.
 */
internal fun relocate(jar: InputStream, packages: Map<String, String>, keepManifest: Boolean = false): Map<String, ByteArray> {
    val paths = packages.entries.associate { (from, to) -> from.replace('.', '/') + "/" to to.replace('.', '/') + "/" }
    fun mapName(name: String, separator: Char): String {
        val dotted = paths.entries.firstOrNull { (from, _) -> name.startsWith(from.replace('/', separator)) } ?: return name
        return dotted.value.replace('/', separator) + name.removePrefix(dotted.key.replace('/', separator))
    }
    fun mapText(text: String): String = packages.entries.fold(text) { acc, (from, to) -> acc.replace("$from.", "$to.") }
    val remapper = object : Remapper() {
        override fun map(internalName: String): String = mapName(internalName, '/')
    }
    val out = linkedMapOf<String, ByteArray>()
    ZipInputStream(jar).use { zip ->
        generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
            val bytes = zip.readBytes()
            when {
                entry.name == "META-INF/MANIFEST.MF" -> if (keepManifest) out[entry.name] = bytes
                entry.name.endsWith(".class") -> {
                    val writer = ClassWriter(0)
                    ClassReader(bytes).accept(ClassRemapper(writer, remapper), 0)
                    out[remapper.map(entry.name.removeSuffix(".class")) + ".class"] = writer.toByteArray()
                }
                entry.name.startsWith("META-INF/services/") ->
                    out["META-INF/services/" + mapName(entry.name.removePrefix("META-INF/services/"), '.')] =
                        mapText(String(bytes, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                // Fabric names entrypoint classes in its metadata, so a relocated runtime class must be renamed there too.
                entry.name == "fabric.mod.json" -> out[entry.name] = mapText(String(bytes, Charsets.UTF_8)).toByteArray(Charsets.UTF_8)
                else -> out[entry.name] = bytes
            }
        }
    }
    return out
}

@DisableCachingByDefault(because = "Rewriting one small jar is faster than fetching it from a cache")
abstract class RelocateLocator : DefaultTask() {
    @get:Input
    abstract val targetPackage: Property<String>

    /** Changes whenever the embedded locator does, so an upgraded plugin relocates again. */
    @get:Input
    abstract val locatorDigest: Property<String>

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun run() {
        val entries = embeddedLocator().inputStream().use { relocate(it, mapOf(LOCATOR_PACKAGE to targetPackage.get())) }
        writeJar(outputJar.get().asFile, entries)
    }
}

internal fun writeJar(file: File, entries: Map<String, ByteArray>) {
    file.parentFile.mkdirs()
    ZipOutputStream(file.outputStream()).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
}

internal fun embeddedLocator(): ByteArray =
    requireNotNull(RelocateLocator::class.java.getResourceAsStream("locator.jar")) { "The Modtrix plugin jar is missing its locator" }.use { it.readBytes() }
