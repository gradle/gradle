// tag::avoid-this[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outFileName = type.get() + ".txt"
        val message = "Hello " + type.get()
        outputDirectory.file(outFileName).get().asFile.writeText(message) // <1>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputDirectory.get().file("a.txt").asFile.readText() // <2>
        outputFile.get().asFile.writeText(message)
    }
}

val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputDirectory = layout.buildDirectory.dir("greetings") // <3>
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputDirectory = layout.buildDirectory.dir("greetings") // <4>
}

tasks.register<ConsumerTask>("consumer") {
    inputDirectory = greeterA.flatMap { it.outputDirectory } // <5>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::avoid-this[]
