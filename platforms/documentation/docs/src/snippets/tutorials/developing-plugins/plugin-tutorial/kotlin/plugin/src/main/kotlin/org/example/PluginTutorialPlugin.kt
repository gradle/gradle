package org.example

import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * A simple 'hello world' plugin.
 */
class PluginTutorialPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        // Register a task
        project.tasks.register("greeting") { task ->
            task.doLast {
                println("Hello from plugin 'org.example.greeting'")
            }
        }
    }
}
