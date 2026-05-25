package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project

abstract class SlackPlugin implements Plugin<Project> {
    void apply(Project project) {
        // Create the 'slack' extension so users can configure the token, channel, and message.
        def extension = project.extensions.create('slack', SlackExtension)

        // Register a task named 'sendTestSlackMessage'.
        project.tasks.register('sendTestSlackMessage') { task ->
            // Use `doLast` to define the action that runs when the task is executed.
            task.doLast {
                println "${extension.message.get()} to ${extension.channel.get()}"
            }
        }
    }
}
