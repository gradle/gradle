// tag::add-action[]
// tag::define-task[]
abstract class GreetingTask : DefaultTask() {
    // end::define-task[]
    @TaskAction
    fun greet() {
        println("hello from GreetingTask")
    }
// tag::define-task[]
}
// end::define-task[]

// Create a task using the task type
tasks.register<GreetingTask>("hello")
// end::add-action[]
