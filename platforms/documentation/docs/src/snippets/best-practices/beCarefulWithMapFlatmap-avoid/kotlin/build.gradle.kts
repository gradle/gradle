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

// tag::config-time-read[]
val generatorTask = tasks.register<GeneratorTask>("generator") {
    outputFile.set(layout.buildDirectory.file("eager-output.txt"))
}

tasks.register<ConsumerTask>("consumeEager") {
    inputFile.set(generatorTask.flatMap { it.outputFile })
    inputContent.set(generatorTask.map {
        it.outputFile.get().asFile.readText() // <1>
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
    from(derivedProducer.flatMap { it.outputFile }) // <1>
    into(layout.buildDirectory.dir("sync"))
}
// end::derived-property[]
