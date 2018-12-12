// A project extension
open class MessageExtension(objects: ObjectFactory) {
    // A configurable greeting
    val greeting: Property<String> = objects.property()
}

// A task that displays a greeting
open class Greeting : DefaultTask() {
    // Configurable by the user
    @Input
    val greeting: Property<String> = project.objects.property()

    // Read-only property calculated from the greeting
    @Internal
    val message: Provider<String> = greeting.map { it + " from Gradle" }

    @TaskAction
    fun printMessage() {
        logger.quiet(message.get())
    }
}

// Create the project extension
val messages = project.extensions.create("messages", MessageExtension::class, project.objects)

// Create the greeting task
tasks.register<Greeting>("greeting") {
    // Attach the greeting from the project extension
    // Note that the values of the project extension have not been configured yet
    greeting.set(messages.greeting)
}

configure<MessageExtension> {
    // Configure the greeting on the extension
    // Note that there is no need to reconfigure the task's `greeting` property. This is automatically updated as the extension property changes
    greeting.set("Hi")
}
