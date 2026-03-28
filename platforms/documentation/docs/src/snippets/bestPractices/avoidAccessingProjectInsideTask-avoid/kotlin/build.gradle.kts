// tag::accessing-project-inside-task-setup[]
abstract class MyTask : DefaultTask() {

    @get:OutputFile
    abstract val myOutput: RegularFileProperty

    @TaskAction
    fun doAction() {
        val outputFile = myOutput.get().asFile
        val outputText = "Current version is: ${project.version}" // <1>
        println(outputText)
        outputFile.writeText(outputText)
    }
}
// end::accessing-project-inside-task-setup[]

// tag::avoid-this[]
tasks.register<MyTask>("avoidThis") {
    myOutput = layout.buildDirectory.get().asFile.resolve("output-avoid.txt")  // <2>
}
// end::avoid-this[]
