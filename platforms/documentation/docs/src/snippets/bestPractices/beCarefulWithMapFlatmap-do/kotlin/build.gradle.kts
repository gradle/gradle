// Task that generates a file
abstract class GeneratorTask : DefaultTask() {
    // This annotation is required for dependency tracking
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

// Correct task wiring
val generator = tasks.register<GeneratorTask>("generate") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

tasks.register<ConsumerTask>("consume") {
    // ✓ Correct: flatMap extracts the Provider property
    inputFile.set(generator.flatMap { it.outputFile })

    // ✓ Correct: Chain map on the Provider to read content
    inputContent.set(
        generator.flatMap { it.outputFile }
            .map { it.asFile.readText() }
    )
}
