/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.basics.transforms

import com.google.common.io.Files
import gradlebuild.basics.classanalysis.ClassAnalysisException
import gradlebuild.basics.classanalysis.ClassDetails
import gradlebuild.basics.classanalysis.ClassGraph
import gradlebuild.basics.classanalysis.JarAnalyzer
import gradlebuild.basics.classanalysis.addJarEntry
import org.gradle.api.artifacts.transform.CacheableTransform
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream


@CacheableTransform
abstract class Minify : TransformAction<Minify.Parameters> {

    interface Parameters : TransformParameters {
        @get:Input
        var keepClassesByCoordinates: Map<String, Set<String>>
    }

    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    @get:InputArtifact
    abstract val artifact: Provider<FileSystemLocation>

    private
    val jarArtifactRegex = Regex("""^(.*?)-\d+(\.\d+)*([.-][A-Za-z0-9]+)*\.jar$""")

    private val keepClassesByArtifacts: Map<String, Set<String>> by lazy {
        parameters.keepClassesByCoordinates.mapKeys { it.key.substringAfter(":") }
    }

    override fun transform(outputs: TransformOutputs) {
        val fileName = artifact.get().asFile.name
        val artifactName = extractArtifactName(fileName)
        val classesFilter = keepClassesByArtifacts[artifactName]
        if (classesFilter != null) {
            val nameWithoutExtension = Files.getNameWithoutExtension(fileName)
            minify(artifact.get().asFile, classesFilter, outputs.file("$nameWithoutExtension-min.jar"))
        } else {
            outputs.file(artifact)
        }
    }

    private
    fun extractArtifactName(fileName: String): String {
        return jarArtifactRegex.matchEntire(fileName)
            ?.groupValues
            ?.get(1)
            ?: error("Cannot derive artifact name from: $fileName")
    }

    private
    fun minify(artifact: File, keepClasses: Set<String>, jarFile: File): File {
        val tempDirectory = java.nio.file.Files.createTempDirectory(jarFile.name).toFile()
        val classesDir = tempDirectory.resolve("classes")
        val manifestFile = tempDirectory.resolve("MANIFEST.MF")
        val buildReceiptFile = tempDirectory.resolve("build-receipt.properties")
        val classGraph = JarAnalyzer("", keepClasses, keepClasses, setOf()).analyze(artifact, classesDir, manifestFile, buildReceiptFile)

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
