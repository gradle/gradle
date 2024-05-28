/*
 * Copyright 2024 the original author or authors.
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

package gradlebuild.basics.classanalysis

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class JarPackager {
    /**
     * Creates a JAR containing only the classes from the source JAR and additional JARs that are reachable from the specified "keep" classes.
     */
    fun minify(sourceJar: File, additionalJars: List<File>, outputJar: File, paramsConfig: PackagingParameters.Builder.() -> Unit) {
        val builder = PackagingParameters.Builder()
        paramsConfig(builder)
        val params = builder.build()

        val tempDirectory = java.nio.file.Files.createTempDirectory(outputJar.name).toFile()
        val classesDir = tempDirectory.resolve("classes")
        val manifestFile = tempDirectory.resolve("MANIFEST.MF")
        val resourcesDir = tempDirectory.resolve("resources")
        val analyzer = JarAnalyzer("", params.keepClasses, NameMatcher.Nothing, NameMatcher.Nothing)

        val classGraph = analyzer.analyze(sourceJar, additionalJars, classesDir, manifestFile, resourcesDir)

        createJar(classGraph, classesDir, manifestFile, resourcesDir, params, outputJar)
    }

    private
    fun createJar(classGraph: ClassGraph, classesDir: File, manifestFile: File, resourcesDir: File, params: PackagingParameters, jarFile: File) {
        try {
            JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { jarOutputStream ->
                if (manifestFile.exists()) {
                    jarOutputStream.addJarEntry(JarFile.MANIFEST_NAME, manifestFile)
                }
                val visited = linkedSetOf<ClassDetails>()
                for (classDetails in classGraph.entryPoints) {
                    visitTree(classDetails, classesDir, jarOutputStream, visited)
                }
                for (resource in classGraph.resources) {
                    copyResource(resource, resourcesDir, params.excludeResources, jarOutputStream)
                }
                for (resource in classGraph.transitiveResources) {
                    copyResource(resource, resourcesDir, params.excludeResourcesFromDependencies, jarOutputStream)
                }
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write JAR $jarFile", exception)
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

    private
    fun copyResource(path: String, resourcesDir: File, exclude: NameMatcher, outputStream: JarOutputStream) {
        if (exclude.matches(path)) {
            println("-> Exclude resource $path")
            return
        }

        println("-> Include resource $path")
        val file = resourcesDir.resolve(path)
        outputStream.addJarEntry(path, file)
    }
}
