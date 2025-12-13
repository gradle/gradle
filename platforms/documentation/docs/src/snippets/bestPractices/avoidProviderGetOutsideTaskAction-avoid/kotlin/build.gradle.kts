// tag::provider-get-task-setup[]
abstract class MyTask : DefaultTask() {
    @get:Input
    abstract val myInput: Property<String>

    @get:OutputFile
    abstract val myOutput: RegularFileProperty

    @TaskAction
    fun doAction() {
        val outputFile = myOutput.get().asFile
        val outputText = myInput.get() // <1>
        println(outputText)
        outputFile.writeText(outputText)
    }
}

val currentEnvironment: Provider<String> = providers.gradleProperty("currentEnvironment").orElse("234") // <2>
// end::provider-get-task-setup[]

// tag::avoid-this[]
tasks.register<MyTask>("avoidThis") {
    myInput = "currentEnvironment=${currentEnvironment.get()}"  // <1>
    myOutput = layout.buildDirectory.get().asFile.resolve("output-avoid.txt")  // <2>
}
// end::avoid-this[]
