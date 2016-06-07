package integration

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.apache.tools.ant.util.TeeOutputStream

import java.io.File
import java.io.FileOutputStream

open class CheckSamples : DefaultTask() {

    override fun getDescription() =
        "Checks each sample by running `gradle help` on it."

    @get:InputFiles
    val samples: FileCollection by lazy {
        project.fileTree(project.samplesDir()).apply {
            include("**/*.gradle.kts")
            include("**/gradle-wrapper.properties")
        }
    }

    @get:OutputDirectory
    val outputDir: File by lazy {
        File(project.buildDir, "check-samples")
    }

    @TaskAction
    fun run() {
        project.sampleDirs().forEach { sampleDir ->
            println("Checking ${relativeFile(sampleDir)}...")
            OutputStreamForResultOf(sampleDir).use { stdout ->
                runGradleHelpOn(sampleDir, stdout)
            }
        }
    }

    private fun OutputStreamForResultOf(sampleDir: File) =
        File(outputDir, "${sampleDir.name}.txt").outputStream()

    private fun relativeFile(file: File) =
        file.relativeTo(project.projectDir)

    private fun runGradleHelpOn(projectDir: File, stdout: FileOutputStream) {
        withConnectionFrom(connectorFor(projectDir)) {
            newBuild()
                .forTasks("help")
                .setStandardOutput(TeeOutputStream(System.out, stdout))
                .setStandardError(TeeOutputStream(System.err, stdout))
                .run()
        }
    }
}
