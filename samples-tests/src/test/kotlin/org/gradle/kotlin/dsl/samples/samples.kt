package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.loadPropertiesFrom
import org.gradle.kotlin.dsl.fixtures.rootProjectDir

import java.io.File


internal
val samplesRootDir = File(rootProjectDir, "samples")


internal
fun copySampleProject(from: File, to: File) {
    withMergedGradleProperties(to.resolve("gradle.properties")) {
        from.copyRecursively(to)
        listOf(".gradle", "build").map { File(to, it) }.filter { it.exists() }.forEach {
            it.deleteRecursively()
        }
    }
}


private
fun withMergedGradleProperties(gradlePropertiesFile: File, action: () -> Unit) =
    loadThenDeletePropertiesFrom(gradlePropertiesFile).also { baseProperties ->
        action()
        loadThenDeletePropertiesFrom(gradlePropertiesFile).also { sampleProperties ->
            baseProperties.putAll(sampleProperties)
            gradlePropertiesFile.outputStream().use { baseProperties.store(it, null) }
        }
    }


private
fun loadThenDeletePropertiesFrom(file: File) =
    loadPropertiesFrom(file).also { file.delete() }
