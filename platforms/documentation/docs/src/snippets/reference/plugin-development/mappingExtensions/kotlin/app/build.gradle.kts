plugins {
    application
}
// tag::snippet[]
// Extension class to capture user input
class MyExtension {
    @Input
    var inputParameter: String? = null
}

// Custom task that uses the input from the extension
class MyCustomTask : org.gradle.api.DefaultTask() {
    @Input
    var inputParameter: String? = null

    @TaskAction
    fun executeTask() {
        println("Input parameter: $inputParameter")
    }
}

// Plugin class that configures the extension and task
class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create and configure the extension
        val extension = project.extensions.create("myExtension", MyExtension::class.java)
        // Create and configure the custom task
        project.tasks.register("myTask", MyCustomTask::class.java) {
            group = "custom"
            inputParameter = extension.inputParameter
        }
    }
}
// end::snippet[]
