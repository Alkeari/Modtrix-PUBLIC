package com.alkeari.modtrix.gradle

import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.process.CommandLineArgumentProvider
import org.gradle.language.jvm.tasks.ProcessResources

/** Minecraft ships unobfuscated from 26.1, so Loom applies without remapping and mods are ordinary dependencies. */
private val Project.unobfuscated get() = stonecutterBuild.eval(mc, ">=26.1")

/** The configuration a Fabric mod dependency goes in: Loom's remapping one while Minecraft is obfuscated. */
private fun Project.modConfiguration(name: String) = if (unobfuscated) name else "mod" + name.replaceFirstChar(Char::uppercase)

private fun Project.loomMinecraft() {
    if (unobfuscated) {
        pluginManager.apply("net.fabricmc.fabric-loom")
        dependencies.add("minecraft", "com.mojang:minecraft:$mc")
        return
    }
    pluginManager.apply("net.fabricmc.fabric-loom-remap")
    val loom = extensions.getByType(LoomGradleExtensionAPI::class.java)
    // Parchment has no release for some versions (1.20.5, 1.21.2); those build on Mojang's names without parameter names.
    val parchment = optionalPin("deps.parchment")
    dependencies.add("minecraft", "com.mojang:minecraft:$mc")
    dependencies.add("mappings", loom.layered {
        officialMojangMappings()
        if (parchment != null) parchment("org.parchmentmc.data:parchment-$mc:$parchment@zip")
    })
}

/** Shared code for every loader, compiled against Mojang names; loader nodes compile it again themselves. */
class ModtrixCommonPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNodeConventions(Loader.COMMON)
        project.loomMinecraft()
        project.dependencies.add("compileOnly", "org.spongepowered:mixin:0.8.5")
        project.dependencies.add(project.modConfiguration("compileOnly"), "net.fabricmc:fabric-loader:${project.pin("deps.fabric_loader")}")
    }
}

class ModtrixFabricPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNodeConventions(Loader.FABRIC)
        project.compileCommonSources(Loader.FABRIC)
        project.relocateRuntimeInJar()
        project.loomMinecraft()
        project.dependencies.add(project.modConfiguration("implementation"), "net.fabricmc:fabric-loader:${project.pin("deps.fabric_loader")}")
        project.optionalPin("deps.fabric_api")?.let { project.dependencies.add(project.modConfiguration("implementation"), "net.fabricmc.fabric-api:fabric-api:$it") }
        if (project.runtimeEnabled()) project.addModMenuEntrypoint()
        val loom = project.extensions.getByType(LoomGradleExtensionAPI::class.java)
        // One refmap name on every loader, so a mixin config reads the same whichever jar carries it; unobfuscated Minecraft needs none.
        if (!project.unobfuscated) loom.mixin.defaultRefmapName.set(project.refmapName())
        loom.runs.configureEach {
            when (name) {
                "client" -> client()
                "server" -> server()
            }
            ideConfigGenerated(true)
        }
    }
}

/** Adds the runtime's Mod Menu entrypoint to the mod's fabric.mod.json, so a mod with a ModConfig gets its settings button. */
private fun Project.addModMenuEntrypoint() {
    val entry = "$RUNTIME_PACKAGE.fabric.FabricModMenu"
    tasks.named("processResources", ProcessResources::class.java) {
        inputs.property("modtrixModMenuEntrypoint", entry)
        doLast {
            val file = destinationDir.resolve("fabric.mod.json")
            if (!file.isFile) return@doLast
            @Suppress("UNCHECKED_CAST")
            val json = groovy.json.JsonSlurper().parse(file) as MutableMap<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val entrypoints = (json["entrypoints"] as? MutableMap<String, Any?>) ?: linkedMapOf<String, Any?>().also { json["entrypoints"] = it }
            @Suppress("UNCHECKED_CAST")
            val modmenu = (entrypoints["modmenu"] as? List<Any?>).orEmpty()
            if (entry !in modmenu) entrypoints["modmenu"] = modmenu + entry
            file.writeText(groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(json)))
        }
    }
}

/** Forge 47 for Minecraft 1.20.1 through ModDevGradle's legacy mode, and Forge for 1.20.6 and later through ForgeGradle 7. */
class ModtrixForgePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNodeConventions(Loader.FORGE)
        project.compileCommonSources(Loader.FORGE)
        project.relocateRuntimeInJar()
        // ModDevGradle's legacy mode stops at 1.20.1, so ForgeGradle 7 builds everything after it. Forge runs on Mojang's
        // names from 1.20.6; 1.20.2 to 1.20.4 still run on SRG names, so their jar is renamed to SRG before it ships.
        if (project.stonecutterBuild.eval(project.mc, "<=1.20.1")) applyLegacyForge(project)
        else applyForgeGradle(project, reobfuscate = project.stonecutterBuild.eval(project.mc, "<1.20.6"))
    }

    private fun applyForgeGradle(project: Project, reobfuscate: Boolean) {
        project.pluginManager.apply("net.minecraftforge.gradle")
        val minecraft = project.extensions.getByType(net.minecraftforge.gradle.MinecraftExtensionForProject::class.java)
        val fg = project.extensions.getByType(net.minecraftforge.gradle.ForgeGradleExtension::class.java)
        project.repositories.apply {
            minecraft.mavenizer(this)
            maven(fg.forgeMaven)
            maven(fg.minecraftLibsMaven)
        }
        val forge = minecraft.dependency("net.minecraftforge:forge:${project.mc}-${project.pin("deps.forge")}")
        project.dependencies.add("implementation", forge)
        // A dev run loads the classes and resources folders as two modules, and the runtime's service files then put its
        // forge package in both, which the module system refuses; one folder makes them one module.
        project.extensions.getByType(SourceSetContainer::class.java).configureEach {
            val dir = project.layout.buildDirectory.dir("sourcesSets/$name")
            output.setResourcesDir(dir.map { it.asFile })
            java.destinationDirectory.set(dir)
        }
        minecraft.runs.register("client")
        minecraft.runs.register("server") { args("--nogui") }
        val mixins = project.mixinConfigs(Loader.FORGE)
        if (mixins.isNotEmpty()) {
            project.tasks.named("jar", Jar::class.java) { manifest.attributes(mapOf("MixinConfigs" to mixins.joinToString(","))) }
        }
        if (reobfuscate) registerSrgRename(project, forge, mixins.isNotEmpty())
    }

    /**
     * Renames the jar from Mojang's names to SRG with Forge's own renaming tool, against the mappings the Mavenizer made.
     * Mixins name their targets in strings the renaming tool cannot see, so a mod with mixins also gets the Mixin
     * annotation processor: its refmap carries the SRG names of injection targets, and its renames of `@Shadow` members
     * join the renaming tool's mappings.
     */
    private fun registerSrgRename(project: Project, forge: net.minecraftforge.gradle.MavenizerInstance, mixins: Boolean) {
        val renamingTool = project.configurations.create("modtrixRenamingTool") { isCanBeConsumed = false; isTransitive = false }
        project.dependencies.add(renamingTool.name, "net.minecraftforge:ForgeAutoRenamingTool:1.1.2:all")
        val jar = project.tasks.named("jar", Jar::class.java)
        val classpath = project.configurations.getByName("compileClasspath")
        val srgJar = project.layout.buildDirectory.file(jar.flatMap { it.archiveFileName }.map { "libs/srg/$it" })
        val build = project.layout.buildDirectory
        val tsrg = build.file("modtrix/mojang-to-srg.tsrg")
        val shadows = build.file("modtrix/mixin-shadows.tsrg")
        if (mixins) {
            val refmap = project.refmapName()
            val refmapFile = build.file("modtrix/refmap/$refmap")
            val convert = project.tasks.register("modtrixMixinMappings", MixinMappings::class.java) {
                input.set(project.layout.file(forge.toSrgFile))
                output.set(tsrg)
            }
            project.dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")
            project.tasks.named("compileJava", JavaCompile::class.java) {
                dependsOn(convert)
                outputs.file(refmapFile)
                outputs.file(shadows)
                options.compilerArgumentProviders.add(CommandLineArgumentProvider {
                    listOf(
                        "-AreobfTsrgFile=${tsrg.get().asFile.absolutePath}",
                        "-AoutTsrgFile=${shadows.get().asFile.absolutePath}",
                        "-AoutRefMapFile=${refmapFile.get().asFile.absolutePath}",
                        "-AmappingTypes=tsrg",
                        "-AdefaultObfuscationEnv=searge",
                    )
                })
            }
            jar.configure { from(refmapFile) }
            project.addRefmapToMixinConfigs(refmap)
        }
        project.tasks.register("reobfJar", RenameJar::class.java) {
            group = "build"
            tool.from(renamingTool)
            input.set(jar.flatMap { it.archiveFile })
            if (mixins) {
                mappings.set(tsrg)
                extraMappings.from(shadows)
                mergedMappings.set(build.file("modtrix/mojang-to-srg-with-mixins.tsrg"))
            } else {
                mappings.set(project.layout.file(forge.toSrgFile))
            }
            libraries.from(classpath)
            outputJar.set(srgJar)
        }
        jar.configure { finalizedBy("reobfJar") }
    }

    private fun applyLegacyForge(project: Project) {
        project.pluginManager.apply("net.neoforged.moddev.legacyforge")
        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        val mc = project.mc
        val parchmentVersion = project.optionalPin("deps.parchment")
        val forgeVersion = "$mc-${project.pin("deps.forge")}"
        val modId = project.mod("id")
        project.extensions.configure(LegacyForgeExtension::class.java) {
            version = forgeVersion
            if (parchmentVersion != null) {
                parchment {
                    minecraftVersion.set(mc)
                    mappingsVersion.set(parchmentVersion)
                }
            }
            runs.register("client") { client() }
            runs.register("server") { server() }
            mods.register(modId) { sourceSet(main) }
        }
        val mixins = project.mixinConfigs(Loader.FORGE)
        project.tasks.named("jar", Jar::class.java) {
            if (mixins.isNotEmpty()) manifest.attributes(mapOf("MixinConfigs" to mixins.joinToString(",")))
            finalizedBy("reobfJar")
        }
        // Forge runs on SRG names, so mixins targeting Minecraft need a refmap from the annotation processor.
        if (mixins.isNotEmpty()) {
            val refmap = project.refmapName()
            project.dependencies.add("annotationProcessor", "org.spongepowered:mixin:0.8.5:processor")
            project.extensions.getByType(MixinExtension::class.java).add(main, refmap)
            project.addRefmapToMixinConfigs(refmap)
        }
    }
}

private fun Project.addRefmapToMixinConfigs(refmap: String) {
    tasks.named("processResources", ProcessResources::class.java) {
        filesMatching("*.mixins.json") {
            var first = true
            filter { line ->
                val opening = first && line.trim() == "{"
                first = false
                if (opening) "{\n  \"refmap\": \"$refmap\"," else line
            }
        }
    }
}

class ModtrixNeoForgePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.applyNodeConventions(Loader.NEOFORGE)
        project.compileCommonSources(Loader.NEOFORGE)
        project.relocateRuntimeInJar()
        project.pluginManager.apply("net.neoforged.moddev")
        val main = project.extensions.getByType(SourceSetContainer::class.java).getByName("main")
        val mc = project.mc
        val parchmentVersion = project.optionalPin("deps.parchment")
        val neoForgeVersion = project.pin("deps.neoforge")
        // NeoForge 20.2, 20.3 and 20.5 publish no ModDevGradle bundle, so those nodes compile against vanilla Minecraft from
        // NeoForm plus the NeoForge jar; NeoForge runs on Mojang's names from 20.2, so nothing needs remapping.
        val neoFormVersion = project.optionalPin("deps.neoform")
        val modId = project.mod("id")
        if (neoFormVersion != null) {
            // Minecraft's own dependency list pins slf4j strictly, below the version NeoForge's libraries ask for.
            val universal = project.dependencies.add("compileOnly", "net.neoforged:neoforge:$neoForgeVersion:universal") as ExternalModuleDependency
            universal.exclude(mapOf("group" to "org.slf4j"))
        }
        project.extensions.configure(NeoForgeExtension::class.java) {
            if (neoFormVersion != null) this.neoFormVersion = neoFormVersion else version = neoForgeVersion
            if (parchmentVersion != null) {
                parchment {
                    minecraftVersion.set(mc)
                    mappingsVersion.set(parchmentVersion)
                }
            }
            runs.register("client") { client() }
            runs.register("server") { server() }
            mods.register(modId) { sourceSet(main) }
        }
        // NeoForge reads META-INF/neoforge.mods.toml from 20.5; earlier builds read META-INF/mods.toml.
        if (project.stonecutterBuild.eval(mc, "<1.20.5")) {
            project.tasks.named("processResources", ProcessResources::class.java) {
                filesMatching("META-INF/neoforge.mods.toml") { name = "mods.toml" }
            }
        }
        // FML 1 (NeoForge 20.2 and 20.3) reads a dependency's `mandatory` flag; `type` replaced it in FML 2.
        if (project.stonecutterBuild.eval(mc, "<1.20.4")) {
            project.tasks.named("processResources", ProcessResources::class.java) {
                filesMatching("META-INF/neoforge.mods.toml") {
                    filter { line ->
                        line.replace("""type\s*=\s*"required"""".toRegex(), "mandatory=true")
                            .replace("""type\s*=\s*"optional"""".toRegex(), "mandatory=false")
                    }
                }
            }
        }
    }
}
