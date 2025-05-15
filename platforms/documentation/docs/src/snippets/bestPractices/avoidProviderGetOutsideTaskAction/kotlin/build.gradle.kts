// tag::provider-get-task-setup[]
abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val myInput: Property<String>

    @get:OutputFile
    abstract val myOutput: RegularFileProperty

    @TaskAction
    fun doAction() {
        // Use get in the task action
        val outputFile = myOutput.get().asFile
        val outputText = myInput.get()
        println(outputText)
        outputFile.writeText(outputText)
    }
}
// end::provider-get-task-setup[]

// tag::avoid-this[]
tasks.register<MyTask>("avoidThis") {
    myInput = "currentEnvironment=${providers.gradleProperty("currentEnvironment").get()}"  // <1>
    myOutput = layout.buildDirectory.get().asFile.resolve("output-avoid.txt")  // <2>
}
// end::avoid-this[]

// tag::do-this[]
tasks.register<MyTask>("doThis") {
    myInput = providers.gradleProperty("currentEnvironment").map { "currentEnvironment=$it" }  // <1>
    myOutput = layout.buildDirectory.file("output-do.txt")  // <2>
}
// end::do-this[]
