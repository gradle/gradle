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

                val includeViaMethodRefs = mutableSetOf<ClassDetails>()
                val vm = mutableSetOf<MethodDetails>()
                val methodsToVisit = mutableListOf<MethodDetails>()
                for (classDetails in classGraph.entryPoints) {
                    methodsToVisit.addAll(classDetails.methods.values)
                }
                while (methodsToVisit.isNotEmpty()) {
                    val method = methodsToVisit.removeFirst()
                    if (!vm.add(method)) {
                        continue
                    }
                    includeViaMethodRefs.add(method.owner)
                    methodsToVisit.addAll(method.dependencies)
                }

                val includedClasses = mutableSetOf<ClassDetails>()
                val classesToVisit = mutableListOf<ClassDetails>()
                classesToVisit.addAll(includeViaMethodRefs)
                while (classesToVisit.isNotEmpty()) {
                    val classDetails = classesToVisit.removeFirst()
                    if (!includedClasses.add(classDetails)) {
                        continue
                    }
                    classesToVisit.addAll(classDetails.dependencies)
                }

                println("-> Included classes via method references")
                for (details in includeViaMethodRefs) {
                    println("  -> ${details.outputClassName}")
                }
                println("-> Included classes via other references")
                for (details in includedClasses) {
                    if (!includeViaMethodRefs.contains(details)) {
                        println("  -> ${details.outputClassName}")
                    }
                }

                println("-> Visited methods: ${vm.size}")
                println("-> Methods not required: ${includedClasses.sumOf { classDetails -> classDetails.methods.values.count { !vm.contains(it) } }}")
                println("-> Included classes via method references: ${includeViaMethodRefs.size}")
                println("-> Included classes: ${includedClasses.size}")

                for (classDetails in includedClasses) {
                    copyClass(classDetails, classesDir, jarOutputStream)
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
    fun copyClass(classDetails: ClassDetails, classesDir: File, jarOutputStream: JarOutputStream) {
        if (classDetails.present) {
            val fileName = classDetails.outputClassFilename
            val classFile = classesDir.resolve(fileName)
            jarOutputStream.addJarEntry(fileName, classFile)
        }
    }

    private
    fun copyResource(path: String, resourcesDir: File, exclude: NameMatcher, outputStream: JarOutputStream) {
        if (!exclude.matches(path)) {
            val file = resourcesDir.resolve(path)
            outputStream.addJarEntry(path, file)
        }
    }
}
