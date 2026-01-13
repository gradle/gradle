package com.example.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class HelloPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("helloFromPlugin") {
            group = "example"
            description = "Prints a message from the included plugin build"
            doLast {
                println("Hello from my-plugin! Applied to: ${'$'}{project.path}")
            }
        }
    }
}
