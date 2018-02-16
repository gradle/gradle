package org.gradle

import com.google.common.io.Files
import org.gradle.api.artifacts.transform.ArtifactTransform

import javax.inject.Inject
import java.io.File
import java.util.HashSet

open class MinifyTransform @Inject constructor(val keepClassesByArtifact: Map<String, Set<String>>) : ArtifactTransform() {
    override fun transform(artifact: File): List<File> {
        val name = artifact.name
        for ((key, value) in keepClassesByArtifact) {
            if (name.startsWith(key)) {
                return listOf(minify(artifact, value))
            }
        }
        return listOf(artifact)
    }

    private fun minify(artifact: File, keepClasses: Set<String>): File {
        val out = outputDirectory
        val jarFile = File(out, Files.getNameWithoutExtension(artifact.path) + "-min.jar")
        val classesDir = File(out, "classes")
        val analysisFile = File(out, "analysis.txt")
        ShadedJarCreator(setOf(artifact), jarFile, analysisFile, classesDir, "", keepClasses, keepClasses, HashSet<String>()).createJar()
        return jarFile
    }
}



