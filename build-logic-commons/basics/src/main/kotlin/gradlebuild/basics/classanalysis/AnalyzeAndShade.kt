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
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.MethodRemapper
import org.objectweb.asm.commons.Remapper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
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
    private val shadowPackage: String?,
    private val keepClasses: NameMatcher,
    private val unshadedClasses: NameMatcher,
    private val ignoreClasses: NameMatcher
) {
    fun analyze(jarFile: File, additionalJars: List<File>, classesDir: File, manifestFile: File, resourcesDir: File): ClassGraph {
        val classGraph = classGraph()

        val seen = mutableSetOf<String>()
        analyzeJar(jarFile, false, classGraph, classesDir, manifestFile, resourcesDir, seen)
        for (file in additionalJars) {
            analyzeJar(file, true, classGraph, classesDir, manifestFile, resourcesDir, seen)
        }

        println("-> entry points: ${classGraph.entryPoints.size}")
        println("-> classes: ${classGraph.classes.size}")
        println("-> found methods: ${classGraph.classes.values.sumOf { it.methods.size }}")
        println("-> found method dependencies: ${classGraph.classes.values.sumOf { classDetails -> classDetails.methods.values.sumOf { it.dependencies.size } }}")

        return classGraph
    }

    private
    fun analyzeJar(jarFile: File, transitive: Boolean, classGraph: ClassGraph, classesDir: File, manifestFile: File, resourcesDir: File, seen: MutableSet<String>) {
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
            keepClasses,
            unshadedClasses,
            ignoreClasses,
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
                visitManifest(file, manifestFile)
            }

            else -> {
                visitResource(file, entry, transitive, classGraph, resourcesDir)
            }
        }
    }

    private
    fun visitManifest(file: ZipInputStream, manifestFile: File) {
        Files.copy(file, manifestFile.toPath())
    }

    private
    fun visitResource(file: ZipInputStream, entry: ZipEntry, transitive: Boolean, classGraph: ClassGraph, resourcesDir: File) {
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

    private
    fun visitClassFile(file: ZipInputStream, entry: ZipEntry, classes: ClassGraph, classesDir: File) {
        try {
            val reader = ClassReader(file)
            val thisClass = classes.visitClass(entry.name, reader.className)

            var currentMethod: MethodDetails? = null
            val classWriter = ClassWriter(0)
            val nonCollecting = DefaultRemapper(classes)
            val collecting = DependencyCollectingRemapper(thisClass, classes)
            reader.accept(
                object : ClassRemapper(classWriter, collecting) {
                    override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor {
                        currentMethod = thisClass.method(name, descriptor)
                        return super.visitMethod(access, name, descriptor, signature, exceptions)
                    }

                    override fun createMethodRemapper(methodVisitor: MethodVisitor): MethodVisitor {
                        val methodDetails = currentMethod!!
                        currentMethod = null
                        return object : MethodRemapper(methodVisitor, collecting) {
                            override fun visitMethodInsn(opcodeAndSource: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                                if (!ignoredPackagePatterns.matches(owner)) {
                                    methodDetails.dependencies.add(classes[owner].method(name, descriptor))
                                }
                                MethodRemapper(methodVisitor, nonCollecting).visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
                            }
                        }
                    }
                },
                ClassReader.EXPAND_FRAMES
            )

            classesDir.resolve(thisClass.outputClassFilename).apply {
                parentFile.mkdirs()
                writeBytes(classWriter.toByteArray())
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not transform class from ${entry.name}", exception)
        }
    }
}

private
class DependencyCollectingRemapper(val thisClass: ClassDetails, val classes: ClassGraph) : Remapper() {
    override fun map(name: String): String {
        if (ignoredPackagePatterns.matches(name)) {
            return name
        }
        val dependencyDetails = classes[name]
        if (dependencyDetails !== thisClass) {
            thisClass.dependencies.add(dependencyDetails)
        }
        return dependencyDetails.outputClassName
    }
}

internal
class DefaultRemapper(val classes: ClassGraph) : Remapper() {
    override fun map(name: String): String {
        if (ignoredPackagePatterns.matches(name)) {
            return name
        }
        val dependencyDetails = classes[name]
        return dependencyDetails.outputClassName
    }
}

fun JarOutputStream.addJarEntry(entryName: String, inputStream: InputStream) {
    val entry = ZipEntry(entryName)
    entry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
    putNextEntry(entry)
    inputStream.copyTo(this)
    closeEntry()
}

fun JarOutputStream.addJarEntry(entryName: String, bytes: ByteArray) {
    val entry = ZipEntry(entryName)
    entry.time = CONSTANT_TIME_FOR_ZIP_ENTRIES
    putNextEntry(entry)
    write(bytes)
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
