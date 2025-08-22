// tag::avoid-this[]
abstract class CustomTask : DefaultTask() {
    @get:Input
    abstract val flavor: Property<String>
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun run() {
        File(outputDirectory.get().asFile, flavor.get()).writeText("Hello") // <1>
    }
}

abstract class ConsumerTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val atxt = File(inputDirectory.get().asFile, "a.txt") // <2>
        outputFile.get().asFile.writeText(atxt.readText())
    }
}

val taskA = tasks.register<CustomTask>("taskA") {
    flavor = "a.txt"
    outputDirectory = layout.buildDirectory.dir("shared") // <3>
}
tasks.register<CustomTask>("taskB") {
    flavor = "b.txt"
    outputDirectory = layout.buildDirectory.dir("shared") // <4>
}
tasks.register<ConsumerTask>("consumer") {
    inputDirectory = taskA.flatMap { it.outputDirectory } // <5>
    outputFile = layout.buildDirectory.file("consumerOutput.txt")
}
// end::avoid-this[]
