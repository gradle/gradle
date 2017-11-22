package org.gradle.kotlin.dsl.resolver

import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.File
import java.util.zip.ZipFile

/**
 * This dependency transform is responsible for extracting the sources from
 * a downloaded ZIP of the Gradle sources, and will return the list of main sources
 * subdirectories for all subprojects.
 */
class ExtractGradleSourcesTransform : ArtifactTransform() {
    fun Any?.discard() = Unit

    override
    fun transform(input: File): List<File> {
        ZipFile(input).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                zip.getInputStream(entry).use { input ->
                    val out = File(outputDirectory, entry.name)
                    if (!out.parentFile.exists()) {
                        out.parentFile.mkdirs()
                    }
                    if (entry.isDirectory) {
                        out.mkdir().discard()
                    } else {
                        out.outputStream().use { output ->
                            input.copyTo(output)
                        }.discard()
                    }

                }
            }
        }

        return sourceDirectories()
    }

    private
    fun sourceDirectories() = outputDirectory.walk().filter(this::isSourceDirectory).toList()

    private
    fun isSourceDirectory(file: File) =
        file.isDirectory && file.parentFile.name == "main" && file.parentFile.parentFile.name == "src"
}
