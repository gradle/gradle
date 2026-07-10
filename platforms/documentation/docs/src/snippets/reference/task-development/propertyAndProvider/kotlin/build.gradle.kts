abstract class Greeting : DefaultTask() { // <1>
    @get:Input
    abstract val greeting: Property<String> // <2>

    @Internal
    val message: Provider<String> = greeting.map { it + " from Gradle" } // <3>

    @TaskAction
    fun printMessage() {
        logger.quiet(message.get())
    }
}

tasks.register<Greeting>("greeting") {
    greeting.set("Hi") // <4>
    greeting = "Hi" // <5>
}
