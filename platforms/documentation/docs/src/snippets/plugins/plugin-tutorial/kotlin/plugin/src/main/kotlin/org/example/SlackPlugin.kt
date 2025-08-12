// tag::final[]
package org.example

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.flow.*
import javax.inject.Inject

abstract class SlackPlugin : Plugin<Project> {
    @Inject
    abstract fun getFlowScope(): FlowScope

    @Inject
    abstract fun getFlowProviders(): FlowProviders

    override fun apply(project: Project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        val extension = project.extensions.create("slack", SlackExtension::class.java)

        // Register a task named 'sendTestSlackMessage' of type SlackTask
        val taskProvider: TaskProvider<SlackTask> = project.tasks.register("sendTestSlackMessage", SlackTask::class.java)

        // Configure the task using values from the extension
        taskProvider.configure {
            it.group = "notification" // Logical task grouping for help output
            it.description = "Sends a test message to Slack using the configured token and channel."

            // Bind extension values to the task's input properties
            it.token.set(extension.token)
            it.channel.set(extension.channel)
            it.message.set(extension.message)
        }

        // Hook into the build lifecycle using the dataflow API
        getFlowScope().always(SlackBuildFlowAction::class.java) { spec ->
            spec.parameters.token.set(extension.token)
            spec.parameters.channel.set(extension.channel)
            spec.parameters.buildFailed.set(getFlowProviders().buildWorkResult.map { it.failure.isPresent })
        }
    }
}
// end::final[]

/*
// tag::slack-plugin[]
abstract class SlackPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("greeting") { task ->
            task.doLast {
                println("Hello from plugin 'org.example.greeting'")
            }
        }
    }
}
// end::slack-plugin[]
*/

/*
// tag::slack-extension[]
abstract class SlackPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        val extension = project.extensions.create("slack", SlackExtension::class.java)

        // Register a task that uses the values from the extension
        project.tasks.register("sendTestSlackMessage") {
            it.doLast {
                println("${extension.message.get()} to ${extension.channel.get()}")
            }
        }
    }
}
// end::slack-extension[]
*/

/*
// tag::slack-task[]
import org.gradle.api.tasks.TaskProvider

abstract class SlackPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        val extension = project.extensions.create("slack", SlackExtension::class.java)

        // Register a task named 'sendTestSlackMessage' of type SlackTask
        val taskProvider: TaskProvider<SlackTask> = project.tasks.register("sendTestSlackMessage", SlackTask::class.java)

        // Configure the task using values from the extension
        taskProvider.configure {
            it.group = "notification" // Logical task grouping for help output
            it.description = "Sends a test message to Slack using the configured token and channel."

            // Bind extension values to the task's input properties
            it.token.set(extension.token)
            it.channel.set(extension.channel)
            it.message.set(extension.message)
        }
    }
}
// end::slack-task[]
*/
