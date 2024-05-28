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

package gradlebuild.basics.classanalysis

import org.gradle.api.attributes.Attribute
import org.gradle.api.internal.file.archive.ZipCopyAction.CONSTANT_TIME_FOR_ZIP_ENTRIES
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


private
val ignoredPackagePatterns = NameMatcher.packageHierarchy("java")


object Attributes {
    val artifactType = Attribute.of("artifactType", String::class.java)
    val minified = Attribute.of("minified", Boolean::class.javaObjectType)
}


class JarAnalyzer(
    private val shadowPackage: String,
    private val keepPackages: NameMatcher,
    private val unshadedPackages: NameMatcher,
    private val ignorePackages: NameMatcher
) {
    fun analyze(jarFile: File, additionalJars: List<File>, classesDir: File, manifestFile: File, resourcesDir: File): ClassGraph {
        val classGraph = classGraph()

        val seen = mutableSetOf<String>()
        analyzeJar(jarFile, false, classGraph, classesDir, manifestFile, resourcesDir, seen)
        for (file in additionalJars) {
            analyzeJar(file, true, classGraph, classesDir, manifestFile, resourcesDir, seen)
        }
        return classGraph
    }

    private
    fun analyzeJar(jarFile: File, transitive: Boolean, classGraph: ClassGraph, classesDir: File, manifestFile: File, resourcesDir: File, seen: MutableSet<String>) {
        println("-> Visiting ${jarFile.name}")
        ZipInputStream(BufferedInputStream(FileInputStream(jarFile))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                visitEntry(input, transitive, entry, classGraph, classesDir, manifestFile, resourcesDir, seen)
            }
        }
    }

    private
    fun classGraph() =
        ClassGraph(
            keepPackages,
            unshadedPackages,
            ignorePackages,
            shadowPackage
        )

    private fun visitEntry(file: ZipInputStream, transitive: Boolean, entry: ZipEntry, classGraph: ClassGraph, classesDir: File, manifestFile: File, resourcesDir: File, seen: MutableSet<String>) {
        if (entry.isDirectory || !seen.add(entry.name)) {
            return
        }

        when {
            entry.isClassFilePath() -> {
                visitClassFile(file, entry, classGraph, classesDir)
            }

            entry.isManifestFilePath() -> {
                Files.copy(file, manifestFile.toPath())
            }

            else -> {
                if (transitive) {
                    classGraph.transitiveResources.add(entry.name)
                } else {
                    classGraph.resources.add(entry.name)
                }
                resourcesDir.resolve(entry.name).apply {
                    parentFile.mkdirs()
                    Files.copy(file, toPath())
                }
            }
        }
    }

    private
    fun ZipEntry.isClassFilePath() = name.endsWith(".class")

    private
    fun ZipEntry.isManifestFilePath() = name == "META-INF/MANIFEST.MF"

    private
    fun visitClassFile(file: ZipInputStream, entry: ZipEntry, classes: ClassGraph, classesDir: File) {
        try {
            val reader = ClassReader(file)
            val details = classes[reader.className]
            details.visited = true
            val classWriter = ClassWriter(0)
            reader.accept(
                ClassRemapper(
                    classWriter,
                    object : Remapper() {
                        override fun map(name: String): String {
                            if (ignoredPackagePatterns.matches(name)) {
                                return name
                            }
                            val dependencyDetails = classes[name]
                            if (dependencyDetails !== details) {
                                details.dependencies.add(dependencyDetails)
                            }
                            return dependencyDetails.outputClassName
                        }
                    }
                ),
                ClassReader.EXPAND_FRAMES
            )

            classesDir.resolve(details.outputClassFilename).apply {
                parentFile.mkdirs()
                writeBytes(classWriter.toByteArray())
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not transform class from ${entry.name}", exception)
        }
    }
}

fun JarOutputStream.addJarEntry(entryName: String, sourceFile: File) {
    val entry = ZipEntry(entryName)
    entry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
    putNextEntry(entry)
    BufferedInputStream(FileInputStream(sourceFile)).use { inputStream -> inputStream.copyTo(this) }
    closeEntry()
}


fun File.getClassSuperTypes(): Set<String> {
    if (!path.endsWith(".class")) {
        throw IllegalArgumentException("Not a class file: $path")
    }
    inputStream().use {
        val reader = ClassReader(it)
        return setOf(reader.superName) + reader.interfaces
    }
}
