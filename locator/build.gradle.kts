plugins {
    java
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(17)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
}

repositories {
    maven("https://maven.minecraftforge.net/")
    maven("https://libraries.minecraft.net/")
    mavenCentral()
}

// NeoForge changed its locator API in place between FML 2 and FML 3, so the NeoForge locator compiles against stubs that
// declare both shapes; the stubs never ship.
val stubs = sourceSets.create("stubs")

dependencies {
    compileOnly("net.minecraftforge:fmlloader:1.20.1-47.4.20")
    compileOnly(stubs.output)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
