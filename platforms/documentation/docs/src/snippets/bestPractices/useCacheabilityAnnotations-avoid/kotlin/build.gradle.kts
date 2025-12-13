// tag::avoid-this[]
abstract class BadCalculatorTask : DefaultTask() { // <1>
    @get:Input
    abstract val first: Property<Int>

    @get:Input
    abstract val second: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val result = first.get() + second.get()
        logger.lifecycle("Result: $result")
        outputFile.get().asFile.writeText(result.toString())
    }
}

tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

tasks.register<BadCalculatorTask>("addBad1") {
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("badOutput.txt")
    outputs.cacheIf { true } // <2>
}

tasks.register<BadCalculatorTask>("addBad2") { // <3>
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("badOutput2.txt")
}
// end::avoid-this[]
