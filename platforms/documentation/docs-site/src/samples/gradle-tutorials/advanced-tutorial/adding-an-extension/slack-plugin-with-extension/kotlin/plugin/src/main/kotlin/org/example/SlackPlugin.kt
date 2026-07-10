package org.example

import org.gradle.api.Project
import org.gradle.api.Plugin

abstract class SlackPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        val extension = project.extensions.create("slack", SlackExtension::class.java)

        // Register a task that uses the values from the extension
        project.tasks.register("sendTestSlackMessage") {
            // Use `doLast` to define the action that runs when the task is executed.
            it.doLast {
                println("${extension.message.get()} to ${extension.channel.get()}")
            }
        }
    }
}
