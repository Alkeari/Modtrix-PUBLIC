package com.alkeari.modtrix.gradle

import org.gradle.api.Project

/** What a node's build script reads: `modtrix.dep("cloth_config")` is `deps.cloth_config` for the version being built. */
open class ModtrixExtension(private val project: Project) {
    val minecraftVersion: String
        get() = project.mc

    fun dep(key: String): String = project.pin("deps.$key")

    fun pin(key: String): String = project.pin(key)
}
