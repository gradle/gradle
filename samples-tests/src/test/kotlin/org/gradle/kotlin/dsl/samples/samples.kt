package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.rootProjectDir

import java.io.File
import java.util.Properties


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
    loadPropertiesFrom(gradlePropertiesFile).also { baseProperties ->
        gradlePropertiesFile.delete()
        action()
        loadPropertiesFrom(gradlePropertiesFile).also { sampleProperties ->
            baseProperties.putAll(sampleProperties)
            gradlePropertiesFile.outputStream().use { baseProperties.store(it, null) }
        }
    }


private
fun loadPropertiesFrom(file: File) =
    file.takeIf { it.isFile }?.inputStream()?.use { Properties().apply { load(it) } } ?: Properties()
