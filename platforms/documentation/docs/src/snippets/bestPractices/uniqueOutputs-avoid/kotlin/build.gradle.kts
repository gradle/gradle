// tag::avoid-this[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        outputDirectory.get().file("${type.get()}.txt").asFile.writeText("Hello") // <1>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        outputFile.get().asFile.writeText(inputDirectory.get().file("a.txt").asFile.readText()) // <2>
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
    inputDirectory = taskA.flatMap { it.outputDirectory } // <5>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::avoid-this[]
