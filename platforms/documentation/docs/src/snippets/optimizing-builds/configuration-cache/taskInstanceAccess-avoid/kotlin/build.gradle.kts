abstract class GenerateDataTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        outputFile.get().asFile.writeText("data")
    }
}

// tag::avoid[]
val generateData = tasks.register<GenerateDataTask>("generateData") {
    outputFile = layout.buildDirectory.file("data.json")
}

tasks.register("processData") {
    dependsOn(generateData)
    doLast {
        // BAD: reading another task's property at execution time
        val source = generateData.get().outputFile.get().asFile
        println("Processing ${source.readText()}")
    }
}
// end::avoid[]
