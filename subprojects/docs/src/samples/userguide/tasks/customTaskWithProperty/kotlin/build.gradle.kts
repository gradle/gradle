// tag::add-property[]
open class GreetingTask : DefaultTask() {
    var greeting = "hello from GreetingTask"

    @TaskAction
    fun greet() {
        println(greeting)
    }
}

// Use the default greeting
tasks.register<GreetingTask>("hello")

// Customize the greeting
tasks.register<GreetingTask>("greeting") {
    greeting = "greetings from GreetingTask"
}
// end::add-property[]
