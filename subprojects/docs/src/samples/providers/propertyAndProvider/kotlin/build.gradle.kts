open class Greeting : DefaultTask() {
    // Configurable by the user
    @get:Input
    val message: Property<String> = project.objects.property()

    // Read-only property calculated from the message
    @get:Internal
    val fullMessage: Provider<String> = message.map { it + " from Gradle" }

    @TaskAction
    fun printMessage() {
        logger.quiet(fullMessage.get())
    }
}

task<Greeting>("greeting") {
    // Configure the greeting
    message.set("Hi")
}
