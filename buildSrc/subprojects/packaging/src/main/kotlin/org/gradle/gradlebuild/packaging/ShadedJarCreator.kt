package org.gradle.gradlebuild.packaging

import org.gradle.internal.exceptions.Contextual

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.net.URI
import java.nio.file.Files
import java.nio.file.FileSystems
import java.nio.file.FileVisitor
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry


private
val ignoredPackagePatterns = PackagePatterns(setOf("java"))


open class ShadedJarCreator(
    private val sourceJars: Iterable<File>,
    private val jarFile: File,
    private val analysisFile: File,
    private val classesDir: File,
    private val shadowPackage: String,
    private val keepPackages: Set<String>,
    private val unshadedPackages: Set<String>,
    private val ignorePackages: Set<String>
) {

    fun createJar() {
        val start = System.currentTimeMillis()
        PrintWriter(analysisFile).use { writer ->
            val classes = classGraph()
            analyse(classes, writer)
            writeJar(classes, classesDir, jarFile, writer)
        }
        val end = System.currentTimeMillis()
        println("Analysis took ${end - start}ms.")
    }

    private
    fun classGraph() =
        ClassGraph(
            PackagePatterns(keepPackages),
            PackagePatterns(unshadedPackages),
            PackagePatterns(ignorePackages),
            shadowPackage)

    private
    fun analyse(classes: ClassGraph, writer: PrintWriter) =
        sourceJars.forEach {
            val jarUri = URI.create("jar:${it.toPath().toUri()}")
            FileSystems.newFileSystem(jarUri, emptyMap<String, Any>()).use { jarFileSystem ->
                jarFileSystem.rootDirectories.forEach {
                    visitClassDirectory(it, classes, ignoredPackagePatterns, writer)
                }
            }
        }

    private
    fun visitClassDirectory(dir: Path, classes: ClassGraph, ignored: PackagePatterns, writer: PrintWriter) {
        Files.walkFileTree(dir, object : FileVisitor<Path> {

            private
            var seenManifest: Boolean = false

            override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?) =
                FileVisitResult.CONTINUE

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                writer.print("${file.fileName}: ")
                when {
                    file.isClassFilePath() -> {
                        visitClassFile(file)
                    }
                    file.isUnshadedPropertiesFilePath() -> {
                        writer.println("include")
                        classes.addResource(ResourceDetails(file.toString(), file.toFile()))
                    }
                    file.isUnseenManifestFilePath() -> {
                        seenManifest = true
                        classes.manifest = ResourceDetails(file.toString(), file.toFile())
                    }
                    else -> {
                        writer.println("skipped")
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

                    writer.println("mapped class name: ${details.outputClassName}")
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

    private
    fun writeJar(classes: ClassGraph, classesDir: File, jarFile: File, writer: PrintWriter) {
        try {
            writer.println()
            writer.println("CLASS GRAPH")
            writer.println()
            JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { jarOutputStream ->
                if (classes.manifest != null) {
                    addJarEntry(classes.manifest!!.resourceName, classes.manifest!!.sourceFile, jarOutputStream)
                }
                val visited = linkedSetOf<ClassDetails>()
                for (classDetails in classes.entryPoints) {
                    visitTree(classDetails, classesDir, jarOutputStream, writer, "- ", visited)
                }
                for (resource in classes.resources) {
                    addJarEntry(resource.resourceName, resource.sourceFile, jarOutputStream)
                }
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write shaded Jar $jarFile", exception)
        }
    }

    private
    fun visitTree(
        classDetails: ClassDetails,
        classesDir: File,
        jarOutputStream: JarOutputStream,
        writer: PrintWriter,
        prefix: String,
        visited: MutableSet<ClassDetails>
    ) {

        if (!visited.add(classDetails)) {
            return
        }
        if (classDetails.visited) {
            writer.println(prefix + classDetails.className)
            val fileName = classDetails.outputClassFilename
            val classFile = classesDir.resolve(fileName)
            addJarEntry(fileName, classFile, jarOutputStream)
            for (dependency in classDetails.dependencies) {
                val childPrefix = prefix.prependIndent("  ")
                visitTree(dependency, classesDir, jarOutputStream, writer, childPrefix, visited)
            }
        } else {
            writer.println("$prefix${classDetails.className} (not included)")
        }
    }

    private
    fun addJarEntry(entryName: String, sourceFile: File, jarOutputStream: JarOutputStream) {
        jarOutputStream.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(sourceFile)).use { inputStream -> inputStream.copyTo(jarOutputStream) }
        jarOutputStream.closeEntry()
    }
}


private
class ClassGraph(
    private val keepPackages: PackagePatterns,
    val unshadedPackages: PackagePatterns,
    private val ignorePackages: PackagePatterns,
    shadowPackage: String
) {

    private
    val classes: MutableMap<String, ClassDetails> = linkedMapOf()

    val entryPoints: MutableSet<ClassDetails> = linkedSetOf()
    val resources: MutableSet<ResourceDetails> = linkedSetOf()
    var manifest: ResourceDetails? = null

    internal
    val shadowPackagePrefix =
        if (shadowPackage.isEmpty()) ""
        else shadowPackage.replace('.', '/') + "/"

    fun addResource(resource: ResourceDetails) {
        resources.add(resource)
    }

    operator fun get(className: String) =
        classes.computeIfAbsent(className) {
            val outputClassName = if (unshadedPackages.matches(className)) className else shadowPackagePrefix + className
            ClassDetails(className, outputClassName).also { classDetails ->
                classes[className] = classDetails
                if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                    entryPoints.add(classDetails)
                }
            }
        }
}


private
class ResourceDetails(val resourceName: String, val sourceFile: File)


private
class ClassDetails(val className: String, val outputClassName: String) {
    var visited: Boolean = false
    val dependencies: MutableSet<ClassDetails> = linkedSetOf()
    val outputClassFilename
        get() = "$outputClassName.class"
}


private
class PackagePatterns(givenPrefixes: Set<String>) {

    private
    val prefixes: MutableSet<String> = hashSetOf()

    private
    val names: MutableSet<String> = hashSetOf()

    init {
        givenPrefixes.map { it.replace('.', '/') }.forEach { internalName ->
            names.add(internalName)
            prefixes.add("$internalName/")
        }
    }

    fun matches(packageName: String): Boolean {
        if (names.contains(packageName)) {
            return true
        }
        for (prefix in prefixes) {
            if (packageName.startsWith(prefix)) {
                names.add(packageName)
                return true
            }
        }
        return false
    }
}


@Contextual
class ClassAnalysisException(message: String, cause: Throwable) : RuntimeException(message, cause)
