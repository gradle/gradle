abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        val outFileName = type.get() + ".txt"
        val message = "Hello " + type.get()
        outputDirectory.file(outFileName).get().asFile.writeText(message)
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputDirectory.get().file("a.txt").asFile.readText()
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
