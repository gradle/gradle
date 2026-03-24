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
val generator = tasks.register<GeneratorTask>("generate") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

tasks.register<ConsumerTask>("consumeNaked") {
    // BROKEN: Creating provider {} inside flatMap creates a "naked provider"
    // The resulting provider has NO knowledge of the 'generator' task
    inputFile.set(generator.flatMap { it.outputFile })

    // BROKEN: Don't chain without map
    inputContent.set(generator.flatMap {
        provider { it.outputFile.get().asFile.readText() }
    })
}
// end::naked-provider[]

// tag::config-time-read[]
val generator2 = tasks.register<GeneratorTask>("generate2") {
    outputFile.set(layout.buildDirectory.file("output2.txt"))
}

tasks.register<ConsumerTask>("consumeEager") {
    // BROKEN: Tries to read the file at configuration time
    // The file doesn't exist yet - generator hasn't run!
    inputFile.set(generator2.flatMap { it.outputFile })
    inputContent.set(generator2.map {
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

val producer = tasks.register<ProducerTask>("producer") {
    someDirectory.set(layout.buildDirectory.dir("output"))
}

tasks.register<Sync>("consume") {
    // BROKEN: flatMap on a derived property may lose the task dependency
    from(producer.flatMap { it.outputFile })
    into(layout.buildDirectory.dir("sync"))
}
// end::derived-property[]
