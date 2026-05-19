package org.example

import org.gradle.api.provider.Property

/**
 * The SlackExtension class defines a custom extension for the plugin.
 * This allows users to configure the plugin in their build script via a DSL block, e.g.:
 *
 * slack {
 *     token = "..."
 *     channel = "#general"
 *     message = "Hello from Gradle!"
 * }
 *
 * The `abstract val` declarations are Gradle managed properties: Gradle
 * generates the implementation at runtime through class decoration and
 * creates the `Property<T>` instances automatically. There is no need to
 * declare a constructor or to inject an `ObjectFactory` manually.
 */
abstract class SlackExtension {

    // The Slack API token used to authenticate requests
    abstract val token: Property<String>

    // The name or ID of the Slack channel to send the message to
    abstract val channel: Property<String>

    // The message content to send to the channel
    abstract val message: Property<String>
}
