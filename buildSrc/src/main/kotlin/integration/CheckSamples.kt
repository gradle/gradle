package integration

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.GradleConnector.newConnector
import org.gradle.tooling.ProjectConnection
import java.io.File

open class CheckSamples : DefaultTask() {

    override fun getDescription() =
        "Checks each sample by running `gradle help` on it."

    @get:InputDirectory
    val samples: File by lazy { project.file("samples") }

    @TaskAction
    fun run() {
        sampleDirs().forEach { sampleDir ->
            println("Checking ${relativeFile(sampleDir)}...")
            runGradleHelpOn(sampleDir)
        }
    }

    private fun sampleDirs() =
        samples.listFiles().filter { it.isDirectory }

    private fun relativeFile(file: File) =
        file.relativeTo(project.projectDir)

    private fun runGradleHelpOn(projectDir: File) {
        connectionFor(projectDir)
            .newBuild()
            .forTasks("help")
            .setStandardOutput(System.out)
            .setStandardError(System.err)
            .run()
    }

    private fun connectionFor(projectDir: File): ProjectConnection =
        newConnector()
            .forProjectDirectory(projectDir)
            .useBuildDistribution()
            .connect()
}
