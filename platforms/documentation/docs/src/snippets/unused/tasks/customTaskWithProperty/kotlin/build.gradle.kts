// tag::add-property[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val greeting: Property<String>

    init {
        greeting.convention("hello from GreetingTask")
    }

    @TaskAction
    fun greet() {
        println(greeting.get())
    }
}

// Use the default greeting
tasks.register<GreetingTask>("hello")

// Customize the greeting
tasks.register<GreetingTask>("greeting") {
    greeting = "greetings from GreetingTask"
}
// end::add-property[]
