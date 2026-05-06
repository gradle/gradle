// tag::configurable-task[]
abstract class ConfigurableTask : DefaultTask() {

    @get:Input
    abstract val inputProperty: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    // Static Configuration: Inputs and Outputs defined in the task class
    init {
        group = "custom"
        description = "A configurable task example"
    }

    @TaskAction
    fun executeTask() {
        println("Executing task with input: ${inputProperty.get()} and output: ${outputFile.asFile.get()}")
    }

}

// Dynamic Configuration: Adding inputs and outputs to a task instance
tasks.register("dynamicTask", ConfigurableTask::class) {
    // Set the input property dynamically
    inputProperty = "dynamic input value"

    // Set the output file dynamically
    outputFile = layout.buildDirectory.file("dynamicOutput.txt")
}
// end::configurable-task[]
