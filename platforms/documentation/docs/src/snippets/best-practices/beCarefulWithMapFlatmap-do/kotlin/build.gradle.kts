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

// tag::flatmap-provider[]
val generatorTask = tasks.register<GeneratorTask>("generator") {
    outputFile.set(layout.buildDirectory.file("output.txt"))
}

tasks.register<ConsumerTask>("consumeLazy") {
    inputFile.set(generatorTask.flatMap { it.outputFile }) // <1>
    inputContent.set(
        generatorTask.flatMap { it.outputFile }
            .map { it.asFile.readText() } // <2>
    )
}
// end::flatmap-provider[]

// tag::eager-property[]
abstract class LegacyTask : DefaultTask() {
    // Old-style eager property (before Provider API)
    @get:OutputDirectory
    lateinit var destinationDir: File

    @TaskAction
    fun execute() {
        destinationDir.mkdirs()
        File(destinationDir, "output.txt").writeText("content")
    }
}

val legacy = tasks.register<LegacyTask>("legacy") {
    destinationDir = layout.buildDirectory.dir("docs").get().asFile
}

tasks.register<ConsumerTask>("consumeLegacy") {
    inputFile.fileProvider(legacy.map { File(it.destinationDir, "output.txt") }) // <1>
    inputContent.set(legacy.map { File(it.destinationDir, "output.txt").readText() })
}
// end::eager-property[]

// tag::chaining[]
tasks.register<ConsumerTask>("consumeChained") {
    inputFile.set(generatorTask.flatMap { it.outputFile })
    inputContent.set(
        generatorTask.flatMap { it.outputFile }
            .map { it.asFile.readText() }
            .map { it.uppercase() }
    )
}
// end::chaining[]

// tag::derived-property-fix[]
abstract class ProducerTask @Inject constructor(
    objectFactory: ObjectFactory
) : DefaultTask() {
    @get:Internal
    val someDirectory = objectFactory.directoryProperty()

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

tasks.register<Sync>("consumeDerivedSync") {
    from(derivedProducer.map { it.outputFile.get() }) // <1>
    into(layout.buildDirectory.dir("sync"))
}
// end::derived-property-fix[]

// tag::direct-annotation[]
abstract class DirectProducerTask : DefaultTask() {
    // Directly annotated property - not derived
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        outputFile.get().asFile.writeText("content")
    }
}

val directProducer = tasks.register<DirectProducerTask>("directProducer") {
    outputFile.set(layout.buildDirectory.file("output/output.txt"))
}

tasks.register<Sync>("consumeDirect") {
    from(directProducer.flatMap { it.outputFile }) // <1>
    into(layout.buildDirectory.dir("sync-direct"))
}
// end::direct-annotation[]
