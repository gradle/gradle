package org.example

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.flow.*
import javax.inject.Inject

/**
 * The SlackPlugin class is a Gradle plugin that registers the 'slack' extension
 * and a task, and also hooks into the build lifecycle using a FlowAction.
 */
abstract class SlackPlugin implements Plugin<Project> {
    @Inject
    abstract FlowScope getFlowScope()

    @Inject
    abstract FlowProviders getFlowProviders()

    @Override
    void apply(Project project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        def extension = project.extensions.create('slack', SlackExtension)

        // Register a task named 'sendTestSlackMessage' of type SlackTask
        TaskProvider<SlackTask> taskProvider = project.tasks.register('sendTestSlackMessage', SlackTask)

        // Configure the task using values from the extension
        taskProvider.configure {
            it.group = 'notification' // Logical task grouping for help output
            it.description = 'Sends a test message to Slack using the configured token and channel.'

            // Bind extension values to the task's input properties
            it.token.set(extension.token)
            it.channel.set(extension.channel)
            it.message.set(extension.message)
        }

        // Hook into the build lifecycle using the dataflow API
        getFlowScope().always(SlackBuildFlowAction) { spec ->
            spec.parameters.token.set(extension.token)
            spec.parameters.channel.set(extension.channel)
            spec.parameters.buildFailed.set(getFlowProviders().buildWorkResult.map { it.failure.isPresent() })
        }
    }
}
