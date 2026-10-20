pluginManagement {
    repositories {
        maven("https://maven.alkeari.com")
        gradlePluginPortal()
        // Fabric Loom, which Modtrix builds Fabric nodes with, is published only here.
        maven("https://maven.fabricmc.net/")
    }
}

plugins {
    id("com.alkeari.modtrix") version "1.1.0"
}

rootProject.name = "MyMod"
