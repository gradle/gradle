package org.gradle.plugins.config

import org.gradle.api.Plugin
import org.gradle.api.Project

open class ConfigPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        extensions.create("config", ConfigExtension::class.java, project)
    }
}
