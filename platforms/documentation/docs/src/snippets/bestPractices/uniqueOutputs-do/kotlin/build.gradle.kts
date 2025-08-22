// tag::do-this[]
abstract class GreetingTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        outputFile.get().asFile.writeText("Hello") // <1>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        outputFile.get().asFile.writeText(inputFile.get().asFile.readText()) // <2>
    }
}

val taskA = tasks.register<GreetingTask>("taskA") {
    outputFile = layout.buildDirectory.file("shared/a.txt") // <3>
}
tasks.register<GreetingTask>("taskB") {
    outputFile = layout.buildDirectory.file("shared/b.txt") // <4>
}
tasks.register<ConsumerTask>("consumer") {
    inputFile = taskA.flatMap { it.outputFile } // <5>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::do-this[]
