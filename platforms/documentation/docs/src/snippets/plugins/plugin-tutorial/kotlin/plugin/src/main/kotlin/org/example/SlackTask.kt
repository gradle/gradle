package org.example

import com.slack.api.Slack
import com.slack.api.methods.request.chat.ChatPostMessageRequest

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import kotlin.text.isNotEmpty

/**
 * A custom Gradle task that sends a test message to a Slack channel.
 *
 * This task is useful for verifying Slack integration during build configuration.
 * It uses the Slack Web API and requires a bot token and a target channel.
 */
abstract class SlackTask : DefaultTask() {

    // The Slack bot token (usually starts with "xoxb-") used to authenticate the API request.
    @get:Input
    abstract val token: Property<String>

    // The Slack channel name or ID (e.g., "#builds" or "C12345678") where the message will be sent.
    @get:Input
    abstract val channel: Property<String>

    // The message content to be sent to the specified Slack channel.
    @get:Input
    abstract val message: Property<String>

    /**
     * Sends a message to Slack when the task is executed.
     * Useful for manual testing of Slack integration from the command line.
     */
    @TaskAction
    fun send() {
        // Initialize the Slack client and get the API methods interface
        val slack = Slack.getInstance()
        val methods = slack.methods(token.get())

        // Create a Slack message request
        val request = ChatPostMessageRequest.builder()
            .channel(channel.get())
            .text(message.get())
            .build()

        // Send the message via the Slack API and check for success
        val response = methods.chatPostMessage(request)
        if (response.isOk) {
            logger.lifecycle("Slack message sent successfully to channel ${channel.get()}")
        } else {
            // Fail the build if the Slack API response indicates an error
            logger.error("Failed to send Slack message: ${response.error}")
            throw RuntimeException("Slack message failed: ${response.error}")
        }
    }
}
