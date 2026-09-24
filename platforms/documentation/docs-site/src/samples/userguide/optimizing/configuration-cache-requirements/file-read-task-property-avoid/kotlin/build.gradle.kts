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
    // Eager: file read happens during configuration; tracked correctly, but any change re-runs configuration
    title = file("config/title.txt").readText().trim()
    output = layout.buildDirectory.file("report.txt")
}
// end::avoid[]
