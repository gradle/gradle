// tag::add-property[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    val greeting: Property<String>

    init {
        greeting.convention("hello from GreetingTask")
    }

    @TaskAction
    fun greet() {
        println(greeting)
    }
}

// Use the default greeting
tasks.register<GreetingTask>("hello")

// Customize the greeting
tasks.register<GreetingTask>("greeting") {
    greeting.set("greetings from GreetingTask")
}
// end::add-property[]
