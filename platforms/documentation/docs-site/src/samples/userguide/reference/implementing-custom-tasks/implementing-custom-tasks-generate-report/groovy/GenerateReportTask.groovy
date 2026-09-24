// tag::generate-report-task[]
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class GenerateReportTask extends DefaultTask {

    @InputDirectory
    abstract DirectoryProperty getSourceDirectory()

    @OutputFile
    abstract RegularFileProperty getReportFile()

    @TaskAction
    void generateReport() {
        def sourceDirectory = sourceDirectory.asFile.get()
        def reportFile = reportFile.asFile.get()
        def fileCount = sourceDirectory.listFiles().count { it.isFile() }
        def directoryCount = sourceDirectory.listFiles().count { it.isDirectory() }

        def reportContent = """
            Report for directory: ${sourceDirectory.absolutePath}
            ------------------------------
            Number of files: $fileCount
            Number of subdirectories: $directoryCount
        """.trim()

        reportFile.text = reportContent
        println("Report generated at: ${reportFile.absolutePath}")
    }
}
// end::generate-report-task[]
