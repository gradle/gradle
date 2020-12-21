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
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


private
val ignoredPackagePatterns = PackagePatterns(setOf("java"))


object Attributes {
    val artifactType = Attribute.of("artifactType", String::class.java)
    val minified = Attribute.of("minified", Boolean::class.javaObjectType)
}


class JarAnalyzer(
    private val shadowPackage: String,
    private val keepPackages: Set<String>,
    private val unshadedPackages: Set<String>,
    private val ignorePackages: Set<String>
) {

    fun analyze(jarFile: File, classesDir: File, manifestFile: File, buildReceipt: File): ClassGraph {
        val classGraph = classGraph()

        JarInputStream(FileInputStream(jarFile)).use { jar ->
            if (jar.manifest != null) {
                manifestFile.outputStream().use {
                    jar.manifest.write(it)
                }
            }
            var entry = jar.nextEntry
            while (entry != null) {
                visitClassDirectory(entry, classGraph, classesDir, jar, buildReceipt)
                entry = jar.nextEntry
            }
        }
        return classGraph
    }

    private
    fun classGraph() =
        ClassGraph(
            PackagePatterns(keepPackages),
            PackagePatterns(unshadedPackages),
            PackagePatterns(ignorePackages),
            shadowPackage
        )

    private
    fun visitClassDirectory(dir: ZipEntry, classes: ClassGraph, classesDir: File, jar: JarInputStream, buildReceipt: File) {
        when {
            dir.isClassFilePath() -> {
                dir.visitClassFile(classes, classesDir, jar)
            }
            dir.isBuildReceipt() -> {
                buildReceipt.writeBytes(jar.readAllBytes())
            }
        }
    }

    private
    fun ZipEntry.isClassFilePath() =
        name.endsWith(".class")

    private
    fun ZipEntry.isBuildReceipt() =
        name == "org/gradle/build-receipt.properties"

    private
    fun ZipEntry.visitClassFile(classes: ClassGraph, classesDir: File, jar: JarInputStream) {
        try {
            val reader = ClassReader(jar)
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
            throw ClassAnalysisException("Could not transform class from $name", exception)
        }
    }
}


fun JarOutputStream.addJarEntry(entryName: String, sourceFile: File) {
    putNextEntry(ZipEntry(entryName))
    BufferedInputStream(FileInputStream(sourceFile)).use { inputStream -> inputStream.copyTo(this) }
    closeEntry()
}
