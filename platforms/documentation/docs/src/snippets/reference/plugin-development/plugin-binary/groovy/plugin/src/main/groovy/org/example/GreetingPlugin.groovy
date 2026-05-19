package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.example.GreetingExtension
import org.example.GreetTask

class GreetingPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Create extension and set sensible defaults (conventions)
        def ext = project.extensions.create("greeting", GreetingExtension)  // <1>
        ext.message.convention("Hello from plugin")
        ext.outputFile.convention(project.layout.buildDirectory.file("greeting.txt"))

        // Register task and map extension -> task inputs (lazy wiring)
        project.tasks.register("greet", GreetTask) {    // <2>
            group = "example"
            description = "Writes a greeting to a file"
            message.set(ext.message)
            outputFile.set(ext.outputFile)
        }
    }
}
