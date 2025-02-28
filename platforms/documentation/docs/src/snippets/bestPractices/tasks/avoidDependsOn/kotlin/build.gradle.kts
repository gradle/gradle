// tag::avoid-this[]
abstract class SimplePrintingTask : DefaultTask() {
    @ge:tInput
    abstract val message: Property<String>

    @TaskAction
    fun run() {
        logger.lifecycle(message.get())
    }
}

tasks.register<SimplePrintingTask>("task1") {
    message = "Hello"
}

tasks.register<SimplePrintingTask>("task2") {
    dependsOn(tasks.named("task1")) // <1>
    message = "World"
}
// end::avoid-this[]

// tag::do-this[]
abstract class BetterPrintingTask : DefaultTask() {
    @ge:tInput
    abstract val message: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        logger.lifecycle(message.get())
        outputFile.get().asFile.write(message.get()) // <1>
    }
}

tasks.register<BetterPrintingTask>("task3") {
    message = "Hello"
}

tasks.register<BetterPrintingTask>("task4") {
    inputs.file(tasks.named<BetterPrintingTask>("task3").get().outputFile) // <2>
    message = "World"
}
// end::do-this[]
