// tag::do-this[]
abstract class GreetingTask : DefaultTask() {
    @get:Input
    abstract val type: Property<String>
    @get:OutputFile
    abstract val outputFile: RegularFileProperty // <1>

    @TaskAction
    fun run() {
        val message = "Hello " + type.get()
        outputFile.get().asFile.writeText(message)
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty // <2>
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val message = inputFile.get().asFile.readText()
        outputFile.get().asFile.writeText(message)
    }
}

val greeterA = tasks.register<GreetingTask>("greeterA") {
    type = "a"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("a.txt") } // <3>
}
tasks.register<GreetingTask>("greeterB") {
    type = "b"
    outputFile = layout.buildDirectory.dir("greetings").map { it.file("b.txt") }
}

tasks.register<ConsumerTask>("consumer") {
    inputFile = greeterA.map { it.outputFile.get() } // <4>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::do-this[]
