// Extend the DefaultTask class to create a HelloTask class
abstract class HelloTask : DefaultTask() {
    @TaskAction
    fun hello() {
        println("hello from HelloTask")
    }
}

// Register the hello Task with type HelloTask
tasks.register<HelloTask>("hello") {
    group = "Custom tasks"
    description = "A lovely greeting task."
}
