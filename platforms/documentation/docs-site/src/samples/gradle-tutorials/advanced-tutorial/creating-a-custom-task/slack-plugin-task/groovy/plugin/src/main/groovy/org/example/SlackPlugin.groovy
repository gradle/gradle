import org.gradle.api.tasks.TaskProvider

abstract class SlackPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // Create the 'slack' extension so users can configure token, channel, and message
        def extension = project.extensions.create('slack', SlackExtension)

        // Register a task named 'sendTestSlackMessage' of type SlackTask
        TaskProvider<SlackTask> taskProvider = project.tasks.register('sendTestSlackMessage', SlackTask)

        // Configure the task using values from the extension
        taskProvider.configure {
            // Logical task grouping for help output
            it.group = 'notification'
            // Sends a test message to Slack using the configured token and channel.
            it.description = 'Sends a test message to Slack using the configured token and channel.'

            // Bind extension values to the task's input properties
            it.token.set(extension.token)
            it.channel.set(extension.channel)
            it.message.set(extension.message)
        }
    }
}
