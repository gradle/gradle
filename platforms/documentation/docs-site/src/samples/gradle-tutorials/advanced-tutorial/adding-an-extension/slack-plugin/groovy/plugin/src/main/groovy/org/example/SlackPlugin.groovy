package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class SlackPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Register a task
        project.tasks.register("greeting") {
            doLast {
                println("Hello from plugin 'org.example.greeting'")
            }
        }
    }
}
