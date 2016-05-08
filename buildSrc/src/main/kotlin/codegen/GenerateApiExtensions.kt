package codegen

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.reflect.KClass

open class GenerateApiExtensions : DefaultTask() {

    @OutputDirectory
    var outputDirectory: File? = null

    @TaskAction
    fun generate() =
        listOf(Project::class, CopySpec::class).forEach {
            generateExtensionsFor(it)
        }

    private fun generateExtensionsFor(type: KClass<*>) {
        val outputFile = outputFileFor(type)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(codeForExtensionsOf(type))
    }

    private fun outputFileFor(type: KClass<*>): File =
        File(outputDirectory!!, "${type.qualifiedName!!.replace('.', '/')}Extensions.kt")
}
