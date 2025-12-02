abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputFile
    abstract val outputFile: RegularFileProperty // <1>

    @TaskAction
    fun run() {
        val message = "Hello " + type.get()
        outputDirectory.file(outFileName).get().asFile.writeText(message) // <2>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputFile.get().asFile.text
        outputFile.get().asFile.writeText(message)
    }
}

// tag::simple-do-this[]
val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings")
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings-2") // <1>
}
// end::simple-do-this[]

tasks.register<ConsumerTask>("consumer") {
    inputDirectory = greeterA.flatMap { it.outputDirectory }
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
