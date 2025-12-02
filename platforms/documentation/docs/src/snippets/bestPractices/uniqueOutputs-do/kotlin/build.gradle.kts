// tag::do-this[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outFileName = type.get() + ".txt"
        val outFile = outputDirectory.file(outFileName).get().asFile
        outFile.writeText("Hello") // <1>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val outputText = inputFile.get().asFile.readText()
        outputFile.get().asFile.writeText(outputText) // <2>
    }
}

val taskA = tasks.register<GreetingTask>("taskA") {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("shared") // <3>
}
tasks.register<GreetingTask>("taskB") {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("shared") // <4>
}
tasks.register<ConsumerTask>("consumer") {
    inputFile = taskA.flatMap { it.outputDirectory.file("a.txt") } // <5>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::do-this[]
