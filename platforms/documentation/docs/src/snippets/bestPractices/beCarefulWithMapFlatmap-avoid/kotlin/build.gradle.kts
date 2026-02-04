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

// BROKEN task wiring
val generator = tasks.register<GeneratorTask>("generate") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

// THIS actually breaks (what people really do wrong):
tasks.register<ConsumerTask>("consumeBroken") {
    // BROKEN: Reading the file content using .get()
    inputContent.set(generator.map { it.outputFile.get().asFile.readText() })

    // BROKEN: Creating a provider {} inside flatMap breaks dependency tracking
    // The resulting provider has no knowledge of the 'generator' task
    inputContent.set(generator.flatMap {
        provider { it.outputFile.get().asFile.readText() }
    })
}
