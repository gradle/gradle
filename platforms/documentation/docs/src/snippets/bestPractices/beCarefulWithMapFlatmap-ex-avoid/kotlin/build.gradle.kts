// Task that generates a file
abstract class GeneratorTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        outputFile.get().asFile.writeText("Generated content")
    }
}

// Task that consumes the generated file
abstract class ConsumerTask : DefaultTask() {
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val inputContent: Property<String>

    @TaskAction
    fun consume() {
        println("Input file: ${inputFile.get().asFile}")
        println("Content: ${inputContent.get()}")
    }
}

// tag::naked-provider[]
val generatorTask = tasks.register<GeneratorTask>("generator") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

tasks.register<ConsumerTask>("consumeNaked") {
    inputFile.set(generatorTask.flatMap { it.outputFile })
    inputContent.set(provider { // <1>
        generatorTask.get().outputFile.get().asFile.readText()
    })
}
// end::naked-provider[]
