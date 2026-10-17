package com.alkeari.modtrix.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.eclipse.model.Classpath
import org.gradle.plugins.ide.eclipse.model.EclipseModel
import org.gradle.plugins.ide.eclipse.model.ProjectDependency

private val TEMPLATED = listOf("fabric.mod.json", "META-INF/mods.toml", "META-INF/neoforge.mods.toml", "pack.mcmeta", "*.mixins.json")

/** What every node shares: Java level, identity, repositories and metadata expansion. */
internal fun Project.applyNodeConventions(loader: Loader) {
    pluginManager.apply("java")
    extensions.create("modtrix", ModtrixExtension::class.java, this)
    version = "${mod("version")}+$mc"
    group = mod("group")
    val archives = "${mod("archives_name")}-${loader.id}"
    extensions.configure(BasePluginExtension::class.java) { archivesName.set(archives) }

    val javaVersion = pin("java.version").toInt()
    extensions.configure(JavaPluginExtension::class.java) { toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion)) }
    tasks.withType(JavaCompile::class.java).configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion)
    }

    addRuntime(loader.id)
    addDefaultMetadata(loader)

    val repos = repositories
    repos.mavenCentral()
    repos.exclusiveContent {
        forRepository { repos.maven { setUrl("https://repo.spongepowered.org/repository/maven-public") } }
        filter { includeGroupAndSubgroups("org.spongepowered") }
    }
    repos.exclusiveContent {
        forRepositories(
            repos.maven { setUrl("https://maven.parchmentmc.org") },
            repos.maven { setUrl("https://maven.neoforged.net/releases") },
        )
        filter { includeGroup("org.parchmentmc.data") }
    }

    if (loader != Loader.COMMON) {
        val key = "minecraft_range_${loader.id}"
        val range = requireNotNull(versionPins(mc)[key]) { "Missing '$key' in versions/$mc/gradle.properties" }
        require(isBoundedRange(range)) {
            "'$key=$range' in versions/$mc/gradle.properties has no upper bound: a node claims only the Minecraft versions it was proven on"
        }
        if (runtimeEnabled()) {
            crossedBoundary(range, mc, runtimeBoundaries(embeddedRuntimeEntries(), loader))?.let { boundary ->
                error(
                    "'$key=$range' in versions/$mc/gradle.properties reaches across $boundary, where the Modtrix runtime changes: " +
                        "add a $boundary node for the versions on that side and end this range before it"
                )
            }
        }
    }

    val expansions = metadataExpansions(loader)
    tasks.named("processResources", ProcessResources::class.java) {
        inputs.properties(expansions)
        filesMatching(TEMPLATED) { expand(expansions) }
    }
}

/**
 * Names a metadata file can use: `modId` and the other `mod.*` keys, every pin in `versions/<mc>/gradle.properties`
 * by its template name, `minecraftRange` and `loaderRange` for the loader this node builds, and `forgeLanguageRange`
 * (Forge's major version, which its language loader reports) where Forge is pinned.
 */
internal fun Project.metadataExpansions(loader: Loader): Map<String, String> {
    val pins = versionPins(mc)
    val mods = rootProject.properties.filterKeys { it.startsWith("mod.") }.mapValues { it.value.toString() }
    return buildMap {
        mods.forEach { (key, value) -> put(templateName(key), value) }
        pins.forEach { (key, value) -> put(templateName(key), value) }
        put("modVersion", project.version.toString())
        put("minecraftRange", pins["minecraft_range_${loader.id}"].orEmpty())
        put("loaderRange", pins["deps.${loader.id}_range"].orEmpty())
        pins["deps.forge"]?.let { put("forgeLanguageRange", "[${it.substringBefore('.')},)") }
    }
}

/**
 * Whether a Minecraft range caps the versions it admits: a Maven range (`[1.21.1]`, `[1.20.1,1.20.2)`) needs an upper
 * end, and a Fabric version predicate needs every alternative to be an exact version or to carry a `<` or `<=` bound.
 */
internal fun isBoundedRange(range: String): Boolean {
    val text = range.trim()
    if (text.isEmpty()) return false
    if (text.first() == '[' || text.first() == '(') {
        return Regex("""[\[(]([^,\])]*)(,([^\])]*))?[\])]""").findAll(text).toList().let { ranges ->
            ranges.isNotEmpty() && ranges.all { r -> r.groups[2] == null || r.groupValues[3].isNotBlank() }
        }
    }
    return text.split("||").all { alternative ->
        val terms = alternative.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        terms.isNotEmpty() && terms.none { it == "*" || it.startsWith("~") || it.startsWith("^") || it.contains(".x") } &&
            (terms.any { it.startsWith("<") } || terms.all { it.first().isDigit() || it.startsWith("=") })
    }
}

/**
 * The runtime boundary a node's Minecraft range reaches across, or null: the range may not admit a version older than the
 * newest boundary at or below the node's version, nor the next boundary above it, because the runtime variant a node
 * carries is chosen by its own version and differs on the other side.
 */
internal fun crossedBoundary(range: String, mc: String, boundaries: Collection<String>): String? {
    val below = boundaries.filter { compareVersions(it, mc) <= 0 }.maxWithOrNull(::compareVersions)
    val above = boundaries.filter { compareVersions(it, mc) > 0 }.minWithOrNull(::compareVersions)
    for ((lower, upper) in rangeIntervals(range)) {
        if (below != null && (lower == null || compareVersions(lower, below) < 0)) return below
        if (above != null && upper != null && (compareVersions(upper.first, above) > 0 || compareVersions(upper.first, above) == 0 && upper.second)) return above
    }
    return null
}

/** Each alternative of a Maven range or Fabric version predicate as its lowest admitted version and its upper end (version, inclusive). */
internal fun rangeIntervals(range: String): List<Pair<String?, Pair<String, Boolean>?>> {
    val text = range.trim()
    if (text.startsWith("[") || text.startsWith("(")) {
        return Regex("""([\[(])([^,\])]*)(,([^\])]*))?([\])])""").findAll(text).map { m ->
            val low = m.groupValues[2].trim().ifEmpty { null }
            if (m.groups[3] == null) low to low?.let { it to true }
            else low to m.groupValues[4].trim().ifEmpty { null }?.let { it to (m.groupValues[5] == "]") }
        }.toList()
    }
    return text.split("||").map { alternative ->
        var lower: String? = null
        var upper: Pair<String, Boolean>? = null
        alternative.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { term ->
            when {
                term.startsWith(">=") -> lower = term.removePrefix(">=")
                term.startsWith(">") -> lower = term.removePrefix(">")
                term.startsWith("<=") -> upper = term.removePrefix("<=") to true
                term.startsWith("<") -> upper = term.removePrefix("<") to false
                else -> term.removePrefix("=").let { lower = it; upper = it to true }
            }
        }
        lower to upper
    }
}

/** Loader nodes compile the common sources themselves, so one compiler run produces classes in that loader's names. */
internal fun Project.compileCommonSources(loader: Loader) {
    val common = rootProject.project(":common:$mc")
    evaluationDependsOn(common.path)
    val commonMain = common.extensions.getByType(SourceSetContainer::class.java).getByName("main")
    val generate = "${common.path}:stonecutterGenerate"
    tasks.named("compileJava", JavaCompile::class.java) {
        dependsOn(generate)
        if (runtimeEnabled()) dependsOn("${common.path}:$EXTRACT_RUNTIME")
        source(commonMain.java.srcDirs)
    }
    tasks.named("processResources", ProcessResources::class.java) {
        dependsOn(generate)
        if (runtimeEnabled()) dependsOn("${common.path}:$EXTRACT_RUNTIME")
        from(commonMain.resources.srcDirs)
    }
    // The compiler gets the common sources through compileJava alone, which IDE models never read, so Eclipse-based
    // tooling (Buildship, and JDT LS behind VS Code's Java support) is given the common node as a project reference.
    // It is added to the merged classpath because a Gradle project dependency cannot express it: every node is named
    // after its Minecraft version, so ':common:1.21.1' and ':fabric:1.21.1' share coordinates and resolve as one.
    val commonEclipse = common.eclipseProjectName(Loader.COMMON)
    eclipseProjectName(loader)
    extensions.getByType(EclipseModel::class.java).classpath.file.whenMerged {
        (this as Classpath).entries.add(ProjectDependency("/$commonEclipse"))
    }
    // IntelliJ IDEA turns a dependency on another node's source set output into a module dependency, which gives the
    // loader module the common sources; it is added only while IDEA syncs, so builds never compile against those classes.
    if (System.getProperty("idea.sync.active") == "true") {
        dependencies.add("compileOnly", files(commonMain.output))
    }
}

/** Names the node's Eclipse project after the mod, loader and version, since Buildship would otherwise see several projects named after the version alone. */
internal fun Project.eclipseProjectName(loader: Loader): String {
    pluginManager.apply("eclipse")
    val name = "${rootProject.name}-${loader.id}-$mc"
    extensions.getByType(EclipseModel::class.java).project.name = name
    return name
}

/**
 * The resource folders a node packages from: its own, its loader folder's (shared by every version of that loader, which
 * is where Stonecutter keeps the sources a node builds) and the common one.
 */
internal fun Project.resourceDirs(loader: Loader): List<java.io.File> =
    listOf(file("src/main/resources"), rootProject.file("${loader.id}/src/main/resources"), rootProject.file("common/src/main/resources")).distinct()

/** Every `*.mixins.json` this node packages, from its own, its loader folder's and the common resources. */
internal fun Project.mixinConfigs(loader: Loader): List<String> =
    resourceDirs(loader).flatMap { dir -> dir.listFiles { file -> file.name.endsWith(".mixins.json") }.orEmpty().map { it.name } }.distinct().sorted()
