package codegen

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.nio.charset.Charset

open class GenerateClasspathManifest : DefaultTask() {

    var outputDirectory: File? = null

    @Input
    val compileOnly = project.configurations.getByName("compileOnly")

    @Input
    val runtime = project.configurations.getByName("runtime")

    val outputFile: File
        @OutputFile
        get() = File(outputDirectory!!, "${project.name}-classpath.properties")

    @TaskAction
    fun generate() {
        val projects = join(compileOnly.dependencies.map { it.name })
        val runtime = join(runtime.files.map { it.name })
        write("projects=$projects\nruntime=$runtime\n")
    }

    private fun join(ss: List<String>) =
        ss.joinToString(separator = ",")

    private fun write(text: String) {
        outputFile.writeText(text, Charset.forName("utf-8"))
    }
}
