// tag::avoid-this[]
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}

abstract class BadCalculatorTask : DefaultTask() { // <1>
    @get:Input
    abstract val first: Property<Int>

    @get:Input
    abstract val second: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val result = (first.get() + second.get()).toString()
        logger.lifecycle("Result: " + result)
        outputFile.get().asFile.writeText(result)
    }
}

tasks.register<BadCalculatorTask>("addBad1") {
    first.set(10)
    second.set(25)
    outputFile.set(layout.buildDirectory.file("badOutput.txt"))
    outputs.cacheIf { true } // <2>
}

tasks.register<BadCalculatorTask>("addBad2") { // <3>
    first.set(3)
    second.set(7)
    outputFile.set(layout.buildDirectory.file("badOutput2.txt"))
}
// end::avoid-this[]

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
        val result = (first.get() + second.get()).toString()
        logger.lifecycle("Result: " + result)
        outputFile.get().asFile.writeText(result)
    }
}

tasks.register<GoodCalculatorTask>("addGood1") { // <2>
    first.set(10)
    second.set(25)
    outputFile.set(layout.buildDirectory.file("goodOutput.txt"))
}

tasks.register<GoodCalculatorTask>("addGood2") {
    first.set(3)
    second.set(7)
    outputFile.set(layout.buildDirectory.file("goodOutput2.txt"))
}
// end::do-this[]
