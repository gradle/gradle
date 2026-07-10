plugins {
    id("base")
}

abstract class GenerateReportTask : DefaultTask() {
    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val count = sourceDirectory.asFile.get().walkTopDown().count()
        reportFile.asFile.get().writeText("Found $count files")
    }
}

// tag::register-task[]
tasks.register<GenerateReportTask>("generateReport") {
    sourceDirectory = layout.projectDirectory.dir("src/main")
    reportFile = layout.buildDirectory.file("reports/directoryReport.txt")
}

tasks.build {
    dependsOn("generateReport")
}
// end::register-task[]
