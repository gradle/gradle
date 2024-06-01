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
import java.io.PrintWriter
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


object Attributes {
    val artifactType = Attribute.of("artifactType", String::class.java)
    val minified = Attribute.of("minified", Boolean::class.javaObjectType)
}

internal
class JarAnalyzer(
    private val shadowPackage: String?,
    private val keepClasses: NameMatcher,
    private val unshadedClasses: NameMatcher,
    private val excludeClasses: NameMatcher
) {
    fun analyze(jarFile: File, additionalJars: List<File>, log: PrintWriter): ClassGraph {
        val classGraph = ClassGraph(keepClasses, unshadedClasses, excludeClasses, shadowPackage)

        val seen = mutableSetOf<String>()
        analyzeJar(jarFile, classGraph, seen, log)
        for (file in additionalJars) {
            analyzeJar(file, classGraph, seen, log)
        }

        println("-> entry points: ${classGraph.entryPoints.size}")
        println("-> classes: ${classGraph.classes.size}")
        println("-> found methods: ${classGraph.classes.values.sumOf { it.methods.size }}")
        println("-> found method dependencies: ${classGraph.classes.values.sumOf { classDetails -> classDetails.methods.values.sumOf { it.dependencies.size } }}")
        println("-> found subtypes: ${classGraph.classes.values.sumOf { it.subtypes.size }}")

        return classGraph
    }

    private
    fun analyzeJar(jarFile: File, classGraph: ClassGraph, seen: MutableSet<String>, log: PrintWriter) {
        ZipInputStream(BufferedInputStream(FileInputStream(jarFile))).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                visitEntry(input, entry, classGraph, seen, log)
            }
        }
    }

    private fun visitEntry(file: ZipInputStream, entry: ZipEntry, classGraph: ClassGraph, seen: MutableSet<String>, log: PrintWriter) {
        if (entry.isClassFilePath() && seen.add(entry.name)) {
            visitClassFile(file, entry, classGraph, log)
        }
    }

    private
    fun visitClassFile(file: ZipInputStream, entry: ZipEntry, classes: ClassGraph, log: PrintWriter) {
        val reader = ClassReader(file)
        val thisClass = classes.visitClass(entry.name, reader.className)
        if (thisClass.excluded) {
            log.println("-> Do not analyse $thisClass")
            return
        }

        var currentMethod: MethodDetails? = null
        val classWriter = ClassWriter(0)
        val nonCollecting = DefaultRemapper(classes)
        val collecting = DependencyCollectingRemapper(thisClass, classes)
        reader.accept(
            object : ClassRemapper(classWriter, collecting) {
                override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                    if (superName != null) {
                        thisClass.superType(classes[superName])
                    }
                    if (interfaces != null) {
                        for (type in interfaces) {
                            thisClass.superType(classes[type])
                        }
                    }
                    super.visit(version, access, name, signature, superName, interfaces)
                }

                override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<String>?): MethodVisitor {
                    currentMethod = thisClass.method(name, descriptor)
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }

                override fun createMethodRemapper(methodVisitor: MethodVisitor): MethodVisitor {
                    val methodDetails = currentMethod!!
                    currentMethod = null
                    return object : MethodRemapper(methodVisitor, collecting) {
                        override fun visitMethodInsn(opcodeAndSource: Int, owner: String, name: String, descriptor: String, isInterface: Boolean) {
                            methodDetails.dependencies.add(classes[owner].method(name, descriptor))
                            MethodRemapper(methodVisitor, nonCollecting).visitMethodInsn(opcodeAndSource, owner, name, descriptor, isInterface)
                        }
                    }
                }
            },
            ClassReader.EXPAND_FRAMES
        )
    }
}

private
class DependencyCollectingRemapper(val thisClass: ClassDetails, val classes: ClassGraph) : Remapper() {
    override fun map(name: String): String {
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
