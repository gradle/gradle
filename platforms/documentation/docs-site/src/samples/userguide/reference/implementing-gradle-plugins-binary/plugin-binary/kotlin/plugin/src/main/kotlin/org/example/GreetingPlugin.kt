package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project

class GreetingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create extension and set sensible defaults (conventions)
        val ext = project.extensions.create("greeting", GreetingExtension::class.java).apply {  // <1>
            message.convention("Hello from plugin")
            outputFile.convention(project.layout.buildDirectory.file("greeting.txt"))
        }

        // Register task and map extension -> task inputs (lazy wiring)
        project.tasks.register("greet", GreetTask::class.java) {    // <2>
            group = "example"
            description = "Writes a greeting to a file"
            message.set(ext.message)
            outputFile.set(ext.outputFile)
        }
    }
}
