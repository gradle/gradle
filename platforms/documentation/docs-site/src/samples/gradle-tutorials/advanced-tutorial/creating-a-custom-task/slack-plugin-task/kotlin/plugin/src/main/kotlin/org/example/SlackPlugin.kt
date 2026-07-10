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
