package com.alkeari.modtrix.gradle

import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.process.ExecOperations

/** Renames a jar's references to Minecraft from one naming set to another with Forge's ForgeAutoRenamingTool. */
@DisableCachingByDefault(because = "Its output is the mod's own release jar, rebuilt whenever the mod changes")
abstract class RenameJar @Inject constructor(private val exec: ExecOperations) : DefaultTask() {
    @get:Classpath
    abstract val tool: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mappings: RegularFileProperty

    /** Further TSRG1 mappings appended to [mappings], such as the Mixin annotation processor's renames of `@Shadow` members. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extraMappings: ConfigurableFileCollection

    @get:Internal
    abstract val mergedMappings: RegularFileProperty

    @get:CompileClasspath
    abstract val libraries: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun rename() {
        val output = outputJar.get().asFile.apply { parentFile.mkdirs() }
        val extra = extraMappings.files.filter { it.exists() }
        val names = if (extra.isEmpty()) mappings.get().asFile else mergedMappings.get().asFile.apply {
            parentFile.mkdirs()
            writeText((listOf(mappings.get().asFile) + extra).joinToString("") { it.readText().trimEnd() + "\n" })
        }
        val arguments = mutableListOf(
            "--input", input.get().asFile.absolutePath,
            "--output", output.absolutePath,
            "--names", names.absolutePath,
        )
        libraries.files.filter { it.exists() }.forEach { arguments += listOf("--lib", it.absolutePath) }
        exec.javaexec {
            classpath(tool)
            mainClass.set("net.minecraftforge.fart.Main")
            args(arguments)
        }
    }
}
