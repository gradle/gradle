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
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


private
val ignoredPackagePatterns = PackagePatterns(setOf("java"))


// See explanation in `org.gradle.api.internal.file.archive.ZipEntryConstants#CONSTANT_TIME_FOR_ZIP_ENTRIES`
private
val zipEntryBaseTimestamp = LocalDateTime.of(1980, 2, 1, 0, 0, 0)
    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()


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

        val jarUri = URI.create("jar:${jarFile.toPath().toUri()}")
        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { jarFileSystem ->
            jarFileSystem.rootDirectories.forEach {
                visitClassDirectory(it, classGraph, classesDir, manifestFile.toPath(), buildReceipt.toPath())
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
    fun visitClassDirectory(dir: Path, classes: ClassGraph, classesDir: File, manifest: Path, buildReceipt: Path) {
        Files.walkFileTree(
            dir,
            object : FileVisitor<Path> {

                private
                var seenManifest: Boolean = false

                override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?) =
                    FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    when {
                        file.isClassFilePath() -> {
                            visitClassFile(file)
                        }

                        file.isBuildReceipt() -> {
                            Files.copy(file, buildReceipt)
                        }

                        file.isUnseenManifestFilePath() -> {
                            seenManifest = true
                            Files.copy(file, manifest)
                        }
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path?, exc: IOException?) =
                    FileVisitResult.TERMINATE

                override fun postVisitDirectory(dir: Path?, exc: IOException?) =
                    FileVisitResult.CONTINUE

                private
                fun Path.isClassFilePath() =
                    toString().endsWith(".class")

                private
                fun Path.isBuildReceipt() =
                    toString() == "/org/gradle/build-receipt.properties"

                private
                fun Path.isUnseenManifestFilePath() =
                    toString() == "/${JarFile.MANIFEST_NAME}" && !seenManifest

                private
                fun visitClassFile(file: Path) {
                    try {
                        val reader = ClassReader(Files.newInputStream(file))
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
                        throw ClassAnalysisException("Could not transform class from ${file.toFile()}", exception)
                    }
                }
            }
        )
    }
}


fun JarOutputStream.addJarEntry(entryName: String, sourceFile: File) {
    val entry = ZipEntry(entryName)
    entry.time = zipEntryBaseTimestamp
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
