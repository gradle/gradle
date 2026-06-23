package org.example

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

/**
 * The SlackExtension class defines a custom extension for the plugin.
 * This allows users to configure the plugin in their build script via a DSL block, e.g.:
 *
 * slack {
 *     token = "..."
 *     channel = "#general"
 *     message = "Hello from Gradle!"
 * }
 */
abstract class SlackExtension {

    // The Slack API token used to authenticate requests
    final Property<String> token

    // The name or ID of the Slack channel to send the message to
    final Property<String> channel

    // The message content to send to the channel
    final Property<String> message

    @Inject
    SlackExtension(ObjectFactory objects) {
        this.token = objects.property(String)
        this.channel = objects.property(String)
        this.message = objects.property(String)
    }
}
