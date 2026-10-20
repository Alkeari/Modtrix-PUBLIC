plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.2.1"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.neoforged.net/releases/")
    maven("https://maven.kikugie.dev/releases")
    maven("https://maven.minecraftforge.net/")
}

dependencies {
    implementation("dev.kikugie:stonecutter:0.9.8")
    implementation("net.fabricmc:fabric-loom:1.18.4")
    implementation("net.neoforged:moddev-gradle:2.0.148")
    implementation("net.minecraftforge:forgegradle:7.0.40")
    implementation("org.ow2.asm:asm-commons:9.8")
    implementation("org.gradle.toolchains:foojay-resolver:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The locator ships inside the plugin and is relocated into each mod's own package at build time.
evaluationDependsOn(":locator")
val locatorJar = project(":locator").tasks.named<Jar>("jar")

// The runtime sources ship inside the plugin; each node extracts the variants for its loader and Minecraft version.
val runtimeZip = tasks.register<Zip>("runtimeZip") {
    from(rootProject.file("runtime"))
    archiveFileName.set("runtime.zip")
    destinationDirectory.set(layout.buildDirectory.dir("runtime"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// The starter project ships inside the plugin, so modtrixInit writes the same files the public templates folder holds.
// Gradle's default excludes drop .gitignore from any copy, so it travels as gitignore and modtrixInit restores the dot.
val starterGitignore = tasks.register("starterGitignore") {
    val source = rootProject.file("templates/starter/.gitignore")
    val target = layout.buildDirectory.file("starter-extra/gitignore")
    inputs.file(source)
    outputs.file(target)
    doLast { source.copyTo(target.get().asFile, overwrite = true) }
}

val starterZip = tasks.register<Zip>("starterZip") {
    from(rootProject.file("templates/starter")) {
        exclude("build/**", ".gradle/**", "versions/**")
    }
    from(starterGitignore)
    archiveFileName.set("starter.zip")
    destinationDirectory.set(layout.buildDirectory.dir("starter"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.processResources {
    from(locatorJar) {
        into("com/alkeari/modtrix/gradle")
        rename { "locator.jar" }
    }
    from(runtimeZip) {
        into("com/alkeari/modtrix/gradle")
    }
    from(starterZip) {
        into("com/alkeari/modtrix/gradle")
    }
}

tasks.test {
    useJUnitPlatform()
    dependsOn(locatorJar, runtimeZip, starterZip)
}

// maven.alkeari.com is served from this bucket; org.gradle.s3.endpoint in gradle.properties points Gradle at its S3 API.
// The keys come only from the environment, so no credential is ever written into a properties file.
publishing {
    // The plugin markers point at this publication, so they follow the artifact id.
    publications.withType<MavenPublication>().matching { it.name == "pluginMaven" }.configureEach {
        artifactId = "modtrix-gradle-plugin"
    }
    repositories {
        maven {
            name = "alkeari"
            url = uri("s3://alkeari-maven")
            credentials(AwsCredentials::class) {
                accessKey = providers.environmentVariable("MAVEN_ALKEARI_ACCESS_KEY").orNull
                secretKey = providers.environmentVariable("MAVEN_ALKEARI_SECRET_KEY").orNull
            }
        }
    }
}

gradlePlugin {
    website = "https://alkeari.com/modtrix"
    vcsUrl = "https://github.com/Alkeari/Modtrix-PUBLIC"
    plugins {
        create("modtrix") {
            id = "com.alkeari.modtrix"
            implementationClass = "com.alkeari.modtrix.gradle.ModtrixSettingsPlugin"
            displayName = "Modtrix"
            description = "Builds one Minecraft mod source tree for Fabric, Forge and NeoForge across several Minecraft versions."
            tags = listOf("minecraft", "fabric", "forge", "neoforge", "multiloader")
        }
        create("modtrixCommon") {
            id = "com.alkeari.modtrix.common"
            implementationClass = "com.alkeari.modtrix.gradle.ModtrixCommonPlugin"
            displayName = "Modtrix Common"
            description = "The common node of a Modtrix build: sources shared by every loader."
            tags = listOf("minecraft", "fabric", "forge", "neoforge", "multiloader")
        }
        create("modtrixFabric") {
            id = "com.alkeari.modtrix.fabric"
            implementationClass = "com.alkeari.modtrix.gradle.ModtrixFabricPlugin"
            displayName = "Modtrix Fabric"
            description = "A Fabric node of a Modtrix build, one per Minecraft version."
            tags = listOf("minecraft", "fabric", "forge", "neoforge", "multiloader")
        }
        create("modtrixForge") {
            id = "com.alkeari.modtrix.forge"
            implementationClass = "com.alkeari.modtrix.gradle.ModtrixForgePlugin"
            displayName = "Modtrix Forge"
            description = "A Forge node of a Modtrix build, one per Minecraft version."
            tags = listOf("minecraft", "fabric", "forge", "neoforge", "multiloader")
        }
        create("modtrixNeoForge") {
            id = "com.alkeari.modtrix.neoforge"
            implementationClass = "com.alkeari.modtrix.gradle.ModtrixNeoForgePlugin"
            displayName = "Modtrix NeoForge"
            description = "A NeoForge node of a Modtrix build, one per Minecraft version."
            tags = listOf("minecraft", "fabric", "forge", "neoforge", "multiloader")
        }
    }
}
