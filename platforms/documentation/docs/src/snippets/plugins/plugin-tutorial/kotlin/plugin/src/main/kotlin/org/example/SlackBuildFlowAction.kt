package org.example

import com.slack.api.Slack
import com.slack.api.methods.request.chat.ChatPostMessageRequest

import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * A Gradle FlowAction that sends a Slack message at the end of a build.
 * This leverages Gradle's build lifecycle via the dataflow API, which allows actions
 * to run automatically when the build completes—without needing to attach listeners manually.
 */
abstract class SlackBuildFlowAction : FlowAction<SlackBuildFlowAction.Params> {

    private val logger = Logging.getLogger(SlackBuildFlowAction::class.java)

    /**
     * Parameters that are passed to this action when it executes.
     * These are injected by Gradle and used to control what the action does.
     */
    interface Params : FlowParameters {
        /** Slack bot token used to authenticate API requests */
        @get:Input
        val token: Property<String>

        /** Slack channel ID or name to send the message to */
        @get:Input
        val channel: Property<String>

        /** Flag indicating whether the build failed */
        @get:Input
        val buildFailed: Property<Boolean>
    }

    /**
     * Executes the action when the build finishes.
     * Constructs and sends a Slack message indicating whether the build succeeded or failed.
     */
    override fun execute(parameters: Params) {
        // Initialize the Slack client and get the API methods interface
        val slack = Slack.getInstance()
        val methods = slack.methods(parameters.token.get())

        // Compose the message text based on the build result
        val status = if (parameters.buildFailed.get()) "Build failed" else "Build succeeded"

        // Create a Slack message request
        val request = ChatPostMessageRequest.builder()
            .channel(parameters.channel.get())
            .text(status)
            .build()

        // Send the message via the Slack API and check for success
        val response = methods.chatPostMessage(request)
        if (response.isOk) {
            logger.lifecycle("Slack message sent successfully to channel ${response.channel}")
        } else {
            logger.error("Failed to send Slack message: ${response.error}")
            throw RuntimeException("Slack message failed: ${response.error}")
        }
    }
}
