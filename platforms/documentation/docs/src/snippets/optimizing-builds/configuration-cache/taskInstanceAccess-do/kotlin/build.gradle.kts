abstract class GenerateDataTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        outputFile.get().asFile.writeText("data")
    }
}

// tag::do[]
abstract class ProcessDataTask : DefaultTask() {
    @get:InputFile
    abstract val source: RegularFileProperty // <1>

    @TaskAction
    fun run() {
        println("Processing ${source.get().asFile.readText()}")
    }
}

val generateData = tasks.register<GenerateDataTask>("generateData") {
    outputFile = layout.buildDirectory.file("data.json")
}

tasks.register<ProcessDataTask>("processData") {
    source = generateData.flatMap { it.outputFile } // <2>
}
// end::do[]
