// tag::avoid[]
abstract class GenerateReportTask : DefaultTask() {
    @get:Input
    abstract val title: Property<String>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun run() {
        output.get().asFile.writeText("Title: ${title.get()}")
    }
}

tasks.register<GenerateReportTask>("generateReport") {
    // BAD: file read happens during configuration
    title = file("config/title.txt").readText().trim()
    output = layout.buildDirectory.file("report.txt")
}
// end::avoid[]
