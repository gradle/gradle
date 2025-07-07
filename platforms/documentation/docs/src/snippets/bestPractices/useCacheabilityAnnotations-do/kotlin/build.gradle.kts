// tag::do-this[]
@CacheableTask // <1>
abstract class GoodCalculatorTask : DefaultTask() {
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

tasks.register<GoodCalculatorTask>("addGood1") { // <2>
    first = 10
    second = 25
    outputFile = layout.buildDirectory.file("goodOutput.txt")
}

tasks.register<GoodCalculatorTask>("addGood2") {
    first = 3
    second = 7
    outputFile = layout.buildDirectory.file("goodOutput2.txt")
}
// end::do-this[]
