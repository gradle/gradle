// tag::generate-report-task[]
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateReportTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun generateReport() {
        val sourceDirectory = sourceDirectory.asFile.get()
        val reportFile = reportFile.asFile.get()
        val fileCount = sourceDirectory.listFiles().count { it.isFile }
        val directoryCount = sourceDirectory.listFiles().count { it.isDirectory }

        val reportContent = """
            |Report for directory: ${sourceDirectory.absolutePath}
            |------------------------------
            |Number of files: $fileCount
            |Number of subdirectories: $directoryCount
        """.trimMargin()

        reportFile.writeText(reportContent)
        println("Report generated at: ${reportFile.absolutePath}")
    }
}
// end::generate-report-task[]
