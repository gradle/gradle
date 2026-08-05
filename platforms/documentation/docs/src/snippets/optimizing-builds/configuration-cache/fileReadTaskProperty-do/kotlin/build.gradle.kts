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

// tag::do[]
tasks.register<GenerateReportTask>("generateReport") {
    title = providers.fileContents(layout.projectDirectory.file("config/title.txt")) // <1>
        .asText
        .map { it.trim() } // <2>
    output = layout.buildDirectory.file("report.txt")
}
// end::do[]
