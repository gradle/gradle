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
abstract class SlackBuildFlowAction implements FlowAction<SlackBuildFlowAction.Params> {

    private final logger = Logging.getLogger(SlackBuildFlowAction)

    /**
     * Parameters that are passed to this action when it executes.
     * These are injected by Gradle and used to control what the action does.
     */
    interface Params extends FlowParameters {
        /** Slack bot token used to authenticate API requests */
        @Input
        Property<String> getToken()

        /** Slack channel ID or name to send the message to */
        @Input
        Property<String> getChannel()

        /** Flag indicating whether the build failed */
        @Input
        Property<Boolean> getBuildFailed()
    }

    /**
     * Executes the action when the build finishes.
     * Constructs and sends a Slack message indicating whether the build succeeded or failed.
     */
    @Override
    void execute(Params parameters) {
        // Initialize the Slack client and get the API methods interface
        def slack = Slack.getInstance()
        def methods = slack.methods(parameters.token.get())

        // Compose the message text based on the build result
        def status = parameters.buildFailed.get() ? 'Build failed' : 'Build succeeded'

        // Create a Slack message request
        def request = ChatPostMessageRequest.builder()
                .channel(parameters.channel.get())
                .text(status)
                .build()

        try {
            // Send the message via the Slack API and check for success
            def response = methods.chatPostMessage(request)
            if (response.isOk()) {
                logger.lifecycle("Slack message sent successfully to channel ${parameters.channel.get()}")
            } else {
                logger.error("Failed to send Slack message: ${response.error}")
                throw new RuntimeException("Slack message failed: ${response.error}")
            }
        } catch (Exception e) {
            logger.error("Exception while sending Slack message", e)
            throw new RuntimeException("Slack message failed", e)
        }
    }
}
