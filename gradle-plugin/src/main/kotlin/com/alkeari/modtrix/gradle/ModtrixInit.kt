package com.alkeari.modtrix.gradle

import java.io.File
import java.util.zip.ZipInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

internal const val MODTRIX_INIT = "modtrixInit"

/** The starter's own identity, which [starterFiles] replaces with the new mod's. */
private const val STARTER_ID = "mymod"
private const val STARTER_NAME = "My Mod"
private const val STARTER_CLASS = "MyMod"
private const val STARTER_GROUP = "com.example"

/** A new mod's identity: its id, display name, Maven group and the class-name stem its entry classes and jars take. */
internal data class ModIdentity(val id: String, val name: String, val group: String, val className: String) {
    companion object {
        /** Fills what is not given from the project folder's name, so `My Cool Mod/` becomes `mycoolmod`, `My Cool Mod` and `MyCoolMod`. */
        fun of(folder: String, id: String?, name: String?, group: String?): ModIdentity {
            val words = (name ?: folder).split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }
            val className = words.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }.ifEmpty { STARTER_CLASS }
            val modId = (id ?: words.joinToString("").lowercase()).ifEmpty { STARTER_ID }
            require(Regex("[a-z][a-z0-9_]{1,63}").matches(modId)) {
                "'$modId' is not a mod id: start with a lowercase letter and use only lowercase letters, digits and underscores (2 to 64 characters)"
            }
            return ModIdentity(modId, name ?: words.joinToString(" ").ifEmpty { STARTER_NAME }, group ?: STARTER_GROUP, className)
        }
    }
}

/**
 * The starter project's files for [identity]: paths and contents with the starter's package, classes, id and name
 * replaced. The wrapper and `settings.gradle.kts` are left out, since a project running this task already has both.
 */
internal fun starterFiles(zip: ByteArray, identity: ModIdentity): Map<String, ByteArray> {
    val starterPackage = "$STARTER_GROUP.$STARTER_ID"
    val newPackage = "${identity.group}.${identity.id}"
    fun rename(text: String) = text
        .replace(starterPackage.replace('.', '/'), newPackage.replace('.', '/'))
        .replace(starterPackage, newPackage)
        .replace("mod.group=$STARTER_GROUP", "mod.group=${identity.group}")
        .replace(STARTER_CLASS, identity.className)
        .replace(STARTER_NAME, identity.name)
        .replace(STARTER_ID, identity.id)
    val files = linkedMapOf<String, ByteArray>()
    ZipInputStream(zip.inputStream()).use { input ->
        generateSequence { input.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
            val name = if (entry.name == "gitignore") ".gitignore" else entry.name
            if (name == "settings.gradle.kts" || name.startsWith("gradle/wrapper/") || name.startsWith("gradlew")) return@forEach
            val bytes = input.readBytes()
            val text = name.endsWith(".java") || name.endsWith(".kts") || name.endsWith(".properties") || name.endsWith(".md") ||
                name.endsWith(".mcmeta") || name.endsWith(".gitignore")
            files[rename(name)] = if (text) rename(bytes.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8) else bytes
        }
    }
    return files
}

internal fun embeddedStarter(): ByteArray =
    requireNotNull(ModtrixInit::class.java.getResourceAsStream("starter.zip")) { "The Modtrix plugin jar is missing its starter project" }.use { it.readBytes() }

/**
 * Writes the starter project into the build's folder for a new mod, named by `-Pmod.id`, `-Pmod.name` and `-Pmod.group`
 * or after the folder. A file that already exists is kept as it is.
 */
@DisableCachingByDefault(because = "It writes a new project once")
abstract class ModtrixInit : DefaultTask() {
    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:Input
    abstract val modId: Property<String>

    @get:Input
    abstract val modName: Property<String>

    @get:Input
    abstract val modGroup: Property<String>

    @get:Input
    abstract val className: Property<String>

    @TaskAction
    fun run() {
        val root = projectDir.get().asFile
        val identity = ModIdentity(modId.get(), modName.get(), modGroup.get(), className.get())
        val written = mutableListOf<String>()
        val kept = mutableListOf<String>()
        starterFiles(embeddedStarter(), identity).forEach { (path, bytes) ->
            val target = File(root, path)
            if (target.exists()) {
                kept += path
            } else {
                target.parentFile.mkdirs()
                target.writeBytes(bytes)
                written += path
            }
        }
        written.forEach { logger.lifecycle("Wrote $it") }
        kept.forEach { logger.lifecycle("Kept existing $it") }
        logger.lifecycle("Run gradlew release to build ${identity.name} for every target in gradle.properties.")
    }
}
