// tag::common-tasks[]
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
// end::common-tasks[]

// tag::naked-provider[]
val nakedGenerator = tasks.register<GeneratorTask>("generateNaked") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

// BROKEN: standalone provider {} has no connection to the generator task.
// Gradle will not add 'generateNaked' to the task graph when 'consumeNaked' runs.
val content: Provider<String> = provider {
    nakedGenerator.get().outputFile.get().asFile.readText()
}

tasks.register<ConsumerTask>("consumeNaked") {
    inputFile.set(nakedGenerator.flatMap { it.outputFile })
    inputContent.set(content)
}
// end::naked-provider[]

// tag::config-time-read[]
val eagerGenerator = tasks.register<GeneratorTask>("generateEager") {
    outputFile.set(layout.buildDirectory.file("eager-output.txt"))
}

tasks.register<ConsumerTask>("consumeEager") {
    // BROKEN: Tries to read the file at configuration time
    // The file doesn't exist yet - generator hasn't run!
    inputFile.set(eagerGenerator.flatMap { it.outputFile })
    inputContent.set(eagerGenerator.map {
        it.outputFile.get().asFile.readText() // FAILS HERE
    })
}
// end::config-time-read[]

// tag::derived-property[]
abstract class ProducerTask @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask() {
    @get:Internal
    val someDirectory = objectFactory.directoryProperty()

    // This property is DERIVED via map - not directly annotated
    @get:OutputFile
    val outputFile = someDirectory.map { it.file("output.txt") }

    @TaskAction
    fun execute() {
        outputFile.get().asFile.writeText("content")
    }
}

val derivedProducer = tasks.register<ProducerTask>("produceDerived") {
    someDirectory.set(layout.buildDirectory.dir("output"))
}

tasks.register<Sync>("consumeDerived") {
    // BROKEN: flatMap on a derived property may lose the task dependency
    from(derivedProducer.flatMap { it.outputFile })
    into(layout.buildDirectory.dir("sync"))
}
// end::derived-property[]
