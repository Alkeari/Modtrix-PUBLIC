package com.alkeari.modtrix.gradle

import java.util.zip.GZIPInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes the Mavenizer's Mojang-to-SRG mappings (gzipped TSRG2 with two namespaces) as TSRG1, the only TSRG form the
 * Mixin annotation processor reads: TSRG2 adds a header line and parameter and static lines indented by two tabs.
 */
@CacheableTask
abstract class MixinMappings : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val input: RegularFileProperty

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun convert() {
        val source = input.get().asFile
        val stream = source.inputStream().let { if (source.name.endsWith(".gz")) GZIPInputStream(it) else it }
        val lines = stream.bufferedReader().useLines { all ->
            all.filterIndexed { index, line -> !(index == 0 && line.startsWith("tsrg2 ")) && !line.startsWith("\t\t") }.toList()
        }
        output.get().asFile.apply { parentFile.mkdirs() }.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}
