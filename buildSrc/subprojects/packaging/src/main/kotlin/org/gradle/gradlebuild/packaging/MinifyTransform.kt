/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.gradlebuild.packaging

import com.google.common.io.Files
import org.gradle.api.artifacts.transform.ArtifactTransform
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import javax.inject.Inject


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
        val manifestFile = outputDirectory.resolve("MANIFEST.MF")
        val classGraph = JarAnalyzer("", keepClasses, keepClasses, setOf()).analyze(artifact, classesDir, manifestFile)

        createJar(classGraph, classesDir, manifestFile, jarFile)

        return jarFile
    }

    private
    fun createJar(classGraph: ClassGraph, classesDir: File, manifestFile: File, jarFile: File) {
        try {
            JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { jarOutputStream ->
                if (manifestFile.exists()) {
                    jarOutputStream.addJarEntry(JarFile.MANIFEST_NAME, manifestFile)
                }
                val visited = linkedSetOf<ClassDetails>()
                for (classDetails in classGraph.entryPoints) {
                    visitTree(classDetails, classesDir, jarOutputStream, visited)
                }
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write shaded Jar $jarFile", exception)
        }
    }

    private
    fun visitTree(
        classDetails: ClassDetails,
        classesDir: File,
        jarOutputStream: JarOutputStream,
        visited: MutableSet<ClassDetails>
    ) {

        if (!visited.add(classDetails)) {
            return
        }
        if (classDetails.visited) {
            val fileName = classDetails.outputClassFilename
            val classFile = classesDir.resolve(fileName)
            jarOutputStream.addJarEntry(fileName, classFile)
            for (dependency in classDetails.dependencies) {
                visitTree(dependency, classesDir, jarOutputStream, visited)
            }
        }
    }
}
