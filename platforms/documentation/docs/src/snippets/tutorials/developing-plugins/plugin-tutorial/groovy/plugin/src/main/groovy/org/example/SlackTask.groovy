package org.example

import com.slack.api.Slack
import com.slack.api.methods.request.chat.ChatPostMessageRequest

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * A custom Gradle task that sends a test message to a Slack channel.
 *
 * This task is useful for verifying Slack integration during build configuration.
 * It uses the Slack Web API and requires a bot token and a target channel.
 */
abstract class SlackTask extends DefaultTask {

    // The Slack bot token (usually starts with "xoxb-") used to authenticate the API request.
    @Input
    abstract Property<String> getToken()

    // The Slack channel name or ID (e.g., "#builds" or "C12345678") where the message will be sent.
    @Input
    abstract Property<String> getChannel()

    // The message content to be sent to the specified Slack channel.
    @Input
    abstract Property<String> getMessage()

    /**
     * Sends a message to Slack when the task is executed.
     * Useful for manual testing of Slack integration from the command line.
     */
    @TaskAction
    void send() {
        // Initialize the Slack client and get the API methods interface
        def slack = Slack.getInstance()
        def methods = slack.methods(token.get())

        // Create a Slack message request
        def request = ChatPostMessageRequest.builder()
                .channel(channel.get())
                .text(message.get())
                .build()

        // Send the message via the Slack API and check for success
        def response = methods.chatPostMessage(request)
        if (response.isOk) {
            logger.lifecycle("Slack message sent successfully to channel ${channel.get()}")
        } else {
            // Fail the build if the Slack API response indicates an error
            logger.error("Failed to send Slack message: ${response.error}")
            throw new RuntimeException("Slack message failed: ${response.error}")
        }
    }
}
