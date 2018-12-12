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

tasks.register<Greeting>("greeting") {
    // Configure the greeting
    greeting.set("Hi")
}
