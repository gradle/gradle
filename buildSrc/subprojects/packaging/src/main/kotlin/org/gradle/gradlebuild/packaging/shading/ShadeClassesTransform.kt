/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.gradlebuild.packaging.shading

import com.google.gson.Gson
import org.gradle.api.artifacts.transform.ArtifactTransform
import org.gradle.gradlebuild.packaging.ignoredPackagePatterns
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile
import javax.inject.Inject

private
const val classTreeFileName = "classTree.json"

private
const val entryPointsFileName = "entryPoints.json"

private
const val relocatedClassesDirName = "classes"

private
const val manifestFileName = "MANIFEST.MF"

open class ShadeClassesTransform @Inject constructor(
    private val shadowPackage: String,
    private val keepPackages: Set<String>,
    private val unshadedPackages: Set<String>,
    private val ignorePackages: Set<String>) : ArtifactTransform() {

    override fun transform(input: File): List<File> {
        val classesDir = outputDirectory.resolve(relocatedClassesDirName)
        classesDir.mkdir()

        val classGraph = ClassGraph(
            PackagePatterns(keepPackages),
            PackagePatterns(unshadedPackages),
            PackagePatterns(ignorePackages),
            shadowPackage
        )

        val jarUri = URI.create("jar:${input.toPath().toUri()}")
        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { jarFileSystem ->
            jarFileSystem.rootDirectories.forEach {
                visitClassDirectory(it, classGraph, ignoredPackagePatterns, classesDir, outputDirectory.resolve(manifestFileName).toPath())
            }
        }

        outputDirectory.resolve(classTreeFileName).bufferedWriter().use {
            Gson().toJson(classGraph.getDependencies(), it)
        }
        outputDirectory.resolve(entryPointsFileName).bufferedWriter().use {
            Gson().toJson(classGraph.entryPoints.map { it.outputClassFilename }, it)
        }

        return listOf(outputDirectory)
    }

    private
    fun visitClassDirectory(dir: Path, classes: ClassGraph, ignored: PackagePatterns, classesDir: File, manifest: Path) {
        Files.walkFileTree(dir, object : FileVisitor<Path> {

            private
            var seenManifest: Boolean = false

            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?) =
                FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                when {
                    file.isClassFilePath()              -> {
                        visitClassFile(file)
                    }
                    file.isUnseenManifestFilePath()     -> {
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
            fun Path.isUnshadedPropertiesFilePath() =
                toString().endsWith(".properties") && classes.unshadedPackages.matches(toString())

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
                    reader.accept(ClassRemapper(classWriter, object : Remapper() {
                        override fun map(name: String): String {
                            if (ignored.matches(name)) {
                                return name
                            }
                            val dependencyDetails = classes[name]
                            if (dependencyDetails !== details) {
                                details.dependencies.add(dependencyDetails)
                            }
                            return dependencyDetails.outputClassName
                        }
                    }), ClassReader.EXPAND_FRAMES)

                    classesDir.resolve(details.outputClassFilename).apply {
                        parentFile.mkdirs()
                        writeBytes(classWriter.toByteArray())
                    }
                } catch (exception: Exception) {
                    throw ClassAnalysisException("Could not transform class from ${file.toFile()}", exception)
                }
            }
        })
    }
}

open class FindClassTrees : ArtifactTransform() {
    override fun transform(input: File): List<File> {
        return listOf(input.resolve(classTreeFileName))
    }

}

open class FindEntryPoints : ArtifactTransform() {
    override fun transform(input: File): List<File> {
        return listOf(input.resolve(entryPointsFileName))
    }

}

open class FindRelocatedClasses : ArtifactTransform() {
    override fun transform(input: File): List<File> {
        return listOf(input.resolve(relocatedClassesDirName))
    }

}

open class FindManifests : ArtifactTransform() {
    override fun transform(input: File): List<File> {
        val manifest = input.resolve(manifestFileName)
        return listOf(manifest).filter { it.exists() }
    }
}
