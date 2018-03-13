package org.gradle.gradlebuild.packaging

import org.gradle.api.artifacts.transform.ArtifactTransform

import com.google.common.io.Files

import javax.inject.Inject
import java.io.File


open class MinifyTransform @Inject constructor(
    private val keepClassesByArtifact: Map<String, Set<String>>
) : ArtifactTransform() {

    override fun transform(artifact: File) =
        keepClassesByArtifact.asSequence()
            .firstOrNull { (key, _) -> artifact.name.startsWith(key) }
            ?.value?.let { keepClasses -> listOf(minify(artifact, keepClasses)) }
            ?: listOf(artifact)

    private
    fun minify(artifact: File, keepClasses: Set<String>): File {
        val jarFile = outputDirectory.resolve("${Files.getNameWithoutExtension(artifact.path)}-min.jar")
        val classesDir = outputDirectory.resolve("classes")
        val analysisFile = outputDirectory.resolve("analysis.txt")
        ShadedJarCreator(setOf(artifact), jarFile, analysisFile, classesDir, "", keepClasses, keepClasses, hashSetOf())
            .createJar()
        return jarFile
    }
}
