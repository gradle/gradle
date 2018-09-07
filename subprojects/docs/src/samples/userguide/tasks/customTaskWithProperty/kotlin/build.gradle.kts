// tag::add-property[]
open class GreetingTask : DefaultTask() {
    var greeting = "hello from GreetingTask"

    @TaskAction
    fun greet() {
        println(greeting)
    }
}

// Use the default greeting
task<GreetingTask>("hello")

// Customize the greeting
task<GreetingTask>("greeting") {
    greeting = "greetings from GreetingTask"
}
// end::add-property[]
