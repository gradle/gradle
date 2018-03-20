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

        println(input.name)
        val jarUri = URI.create("jar:${input.toPath().toUri()}")
        FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { jarFileSystem ->
            jarFileSystem.rootDirectories.forEach {
                visitClassDirectory(it, classGraph, ignoredPackagePatterns, classesDir)
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
    fun visitClassDirectory(dir: Path, classes: ClassGraph, ignored: PackagePatterns, classesDir: File) {
        Files.walkFileTree(dir, object : FileVisitor<Path> {

            private
            var seenManifest: Boolean = false

            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?) =
                FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
//                writer.print("${file.fileName}: ")
                when {
                    file.isClassFilePath()              -> {
                        visitClassFile(file)
                    }
                    file.isUnshadedPropertiesFilePath() -> {
//                        writer.println("include")
                        classes.addResource(ResourceDetails(file.toString(), file.toFile()))
                    }
                    file.isUnseenManifestFilePath()     -> {
                        seenManifest = true
                        classes.manifest = ResourceDetails(file.toString(), file.toFile())
                    }
//                    else                                -> {
//                        writer.println("skipped")
//                    }
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
                toString() == JarFile.MANIFEST_NAME && !seenManifest

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

//                    writer.println("mapped class name: ${details.outputClassName}")
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
