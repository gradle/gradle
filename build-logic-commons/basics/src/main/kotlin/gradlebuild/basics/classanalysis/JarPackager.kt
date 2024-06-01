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

        val analyzer = JarAnalyzer(params.packagePrefix, params.keepClasses, params.unshadedClasses, params.excludeClasses)

        PrintWriter(BufferedOutputStream(FileOutputStream(outputJar.parentFile.resolve("graph.txt")))).use { log ->
            val classGraph = analyzer.analyze(sourceJar, additionalJars, log)

            createJar(sourceJar, additionalJars, classGraph, params, outputJar, log)
        }
    }

    private
    fun createJar(sourceJar: File, additionalJars: List<File>, classGraph: ClassGraph, params: PackagingParameters, outputJar: File, log: PrintWriter) {
        val includedClasses = mutableSetOf<ClassDetails>()

        val includeViaMethodRefs = mutableSetOf<ClassDetails>()
        val includeViaMethodOverride = mutableSetOf<ClassDetails>()

        val seenMethods = mutableSetOf<MethodDetails>()
        val methodsToVisit = mutableListOf<MethodDetails>()
        for (classDetails in classGraph.entryPoints) {
            for (method in classDetails.methods.values) {
                log.println("-> Entry point method: $method")
            }
            methodsToVisit.addAll(classDetails.methods.values)
        }

        for (classDetails in classGraph.entryPoints) {
            log.println("-> Entry point class: $classDetails")
        }
        val classesToVisit = mutableListOf<ClassDetails>()
        classesToVisit.addAll(classGraph.entryPoints)

        val pendingMethodOverrides = mutableSetOf<MethodDetails>()

        while (methodsToVisit.isNotEmpty() || classesToVisit.isNotEmpty()) {
            while (methodsToVisit.isNotEmpty()) {
                val method = methodsToVisit.removeFirst()
                if (!seenMethods.add(method)) {
                    // Already visited this method
                    continue
                }

                val owner = method.owner
                if (!owner.canBeIncluded) {
                    // Don't visit methods for classes that will not be included
                    continue
                }

                includeViaMethodRefs.add(owner)
                classesToVisit.add(owner)

                for (dependency in method.dependencies) {
                    log.println("-> Method call $method -> $dependency")
                }

                // Visit all methods that this method calls
                methodsToVisit.addAll(method.dependencies)

                for (subtype in owner.subtypes) {
                    // Potentially need to visit overridden methods from subtypes that are included
                    val override = subtype.method(method)
                    log.println("-> Pending method override $method -> $override")
                    pendingMethodOverrides.add(override)
                }
            }

            while (classesToVisit.isNotEmpty()) {
                val classDetails = classesToVisit.removeFirst()
                if (!includedClasses.add(classDetails)) {
                    continue
                }

                if (classDetails.supertypes.any { !it.canBeIncluded }) {
                    for (method in classDetails.methods.values) {
                        log.println("-> Class with excluded supertype $classDetails -> $method")
                    }
                    // Don't know which inherited types will be used, so follow all methods
                    methodsToVisit.addAll(classDetails.methods.values)
                }

                for (dependency in classDetails.dependencies) {
                    log.println("-> Class dependency $classDetails -> $dependency")
                }
                classesToVisit.addAll(classDetails.dependencies)
            }

            val iter = pendingMethodOverrides.iterator()
            while (iter.hasNext()) {
                val method = iter.next()
                if (includedClasses.contains(method.owner)) {
                    log.println("-> Method override $method")
                    includeViaMethodOverride.add(method.owner)
                    methodsToVisit.add(method)
                    iter.remove()
                }
            }
        }

        log.println("-> Included classes via method references")
        for (details in includeViaMethodRefs) {
            log.println("  -> ${details.outputClassName}")
        }
        log.println("-> Included classes via method override")
        for (details in includeViaMethodOverride) {
            if (!includeViaMethodRefs.contains(details)) {
                log.println("  -> ${details.outputClassName}")
            }
        }
        log.println("-> Included classes via other references")
        for (details in includedClasses) {
            if (!includeViaMethodRefs.contains(details) && !includedClasses.contains(details)) {
                log.println("  -> ${details.outputClassName}")
            }
        }

        println("-> Visited methods: ${seenMethods.size}")
        println("-> Methods not required: ${includedClasses.sumOf { classDetails -> classDetails.methods.values.count { !seenMethods.contains(it) } }}")
        println("-> Included classes via method references: ${includeViaMethodRefs.size}")
        println("-> Included classes via method override: ${(includeViaMethodOverride - includeViaMethodRefs).size}")
        println("-> Included classes: ${includedClasses.size}")

        try {
            val seen = mutableSetOf<String>()
            JarOutputStream(BufferedOutputStream(FileOutputStream(outputJar))).use { outputStream ->
                copyToJar(sourceJar, outputStream, classGraph, includedClasses, params.excludeResources, seen)
                for (additionalJar in additionalJars) {
                    copyToJar(additionalJar, outputStream, classGraph, includedClasses, params.excludeResourcesFromDependencies, seen)
                }
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write JAR $outputJar", exception)
        }
    }

    private fun copyToJar(jarFile: File, outputStream: JarOutputStream, classGraph: ClassGraph, includedClasses: Set<ClassDetails>, excludeResources: NameMatcher, seen: MutableSet<String>) {
        val remapper = DefaultRemapper(classGraph)
        ZipInputStream(BufferedInputStream(FileInputStream(jarFile))).use { inputStream ->
            while (true) {
                val entry = inputStream.nextEntry ?: break

                if (entry.isDirectory || !seen.add(entry.name)) {
                    // Skip duplicates and directories
                    continue
                }

                if (entry.isManifestFilePath()) {
                    // Always copy manifest
                    outputStream.addJarEntry(entry.name, inputStream)
                } else if (entry.isClassFilePath()) {
                    // Copy class if it is to be included
                    val classDetails = classGraph.forSourceEntry(entry.name)
                    if (classDetails != null && includedClasses.contains(classDetails)) {
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
