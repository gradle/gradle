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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintWriter
import java.util.jar.JarOutputStream
import java.util.zip.ZipInputStream

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
        val analyzer = JarAnalyzer(params.packagePrefix, params.keepClasses, params.unshadedClasses, params.excludeClasses)

        val classGraph = analyzer.analyze(sourceJar, additionalJars, classesDir, manifestFile, resourcesDir)

        createJar(sourceJar, additionalJars, classGraph, params, outputJar)
    }

    private
    fun createJar(sourceJar: File, additionalJars: List<File>, classGraph: ClassGraph, params: PackagingParameters, jarFile: File) {
        val includedClasses = mutableSetOf<ClassDetails>()

        PrintWriter(BufferedOutputStream(FileOutputStream(jarFile.parentFile.resolve("graph.txt")))).use { out ->
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

                for (dependency in method.dependencies) {
                    out.println("-> Method $method -> $dependency")
                }

                methodsToVisit.addAll(method.dependencies)
            }

            val classesToVisit = mutableListOf<ClassDetails>()
            classesToVisit.addAll(includeViaMethodRefs)
            while (classesToVisit.isNotEmpty()) {
                val classDetails = classesToVisit.removeFirst()
                if (!includedClasses.add(classDetails)) {
                    continue
                }
                for (dependency in classDetails.dependencies) {
                    out.println("-> Class $classDetails -> $dependency")
                }
                classesToVisit.addAll(classDetails.dependencies)
            }

            out.println("-> Included classes via method references")
            for (details in includeViaMethodRefs) {
                out.println("  -> ${details.outputClassName}")
            }
            out.println("-> Included classes via other references")
            for (details in includedClasses) {
                if (!includeViaMethodRefs.contains(details)) {
                    out.println("  -> ${details.outputClassName}")
                }
            }

            println("-> Visited methods: ${vm.size}")
            println("-> Methods not required: ${includedClasses.sumOf { classDetails -> classDetails.methods.values.count { !vm.contains(it) } }}")
            println("-> Included classes via method references: ${includeViaMethodRefs.size}")
            println("-> Included classes: ${includedClasses.size}")
        }

        try {
            val seen = mutableSetOf<String>()
            JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { outputStream ->
                copyToJar(sourceJar, outputStream, classGraph, params.excludeClasses, seen)
                for (jar in additionalJars) {
                    copyToJar(jar, outputStream, classGraph, params.excludeClasses, seen)
                }
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write JAR $jarFile", exception)
        }
    }

    private fun copyToJar(jarFile: File, outputStream: JarOutputStream, classGraph: ClassGraph, excludeResources: NameMatcher, seen: MutableSet<String>) {
        val remapper = DefaultRemapper(classGraph)

        ZipInputStream(BufferedInputStream(FileInputStream(jarFile))).use { inputStream ->
            while (true) {
                val entry = inputStream.nextEntry ?: break
                if (entry.isDirectory || !seen.add(entry.name)) {
                    // Skip duplicates and directories
                    return
                }
                if (entry.isManifestFilePath()) {
                    // Always copy manifest
                    outputStream.addJarEntry(entry.name, inputStream)
                } else if (entry.isClassFilePath()) {
                    // Copy class if it is to be included
                    val classDetails = classGraph.forSourceEntry(entry.name)
                    if (classDetails != null) {
                        val reader = ClassReader(inputStream)
                        val writer = ClassWriter(0)
                        reader.accept(ClassRemapper(writer, remapper), ClassReader.EXPAND_FRAMES)
                        outputStream.addJarEntry(classDetails.outputClassFilename, writer.toByteArray())
                    }
                } else if (!excludeResources.matches(entry.name)) {
                    // Copy resource if it is to be included
                    outputStream.addJarEntry(entry.name, inputStream)
                }
            }
        }
    }
}
