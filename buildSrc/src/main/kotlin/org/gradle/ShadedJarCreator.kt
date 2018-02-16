package org.gradle

import com.google.common.io.ByteStreams
import org.gradle.internal.exceptions.Contextual
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import java.io.*
import java.net.URI
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.HashMap
import java.util.HashSet
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

open class ShadedJarCreator(val sourceJars: Iterable<File>, val jarFile: File, val analysisFile: File, val classesDir: File, val shadowPackage: String, val keepPackages: Set<String>, val unshadedPackages: Set<String>, val ignorePackages: Set<String>) {
    fun createJar() {
        val start = System.currentTimeMillis()
        var writer: PrintWriter? = null
        try {
            writer = PrintWriter(analysisFile)
            val classes = ClassGraph(PackagePatterns(keepPackages), PackagePatterns(unshadedPackages), PackagePatterns(ignorePackages), shadowPackage)
            analyse(classes, writer)
            writeJar(classes, classesDir, jarFile, writer)
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
        val end = System.currentTimeMillis()
        println("Analysis took " + (end - start) + "ms.")
    }

    private fun analyse(classes: ClassGraph, writer: PrintWriter) {
        val ignored = PackagePatterns(setOf("java"))

        sourceJars.forEach {
            FileSystems.newFileSystem(URI.create("jar:" + it.toPath().toUri()), HashMap<String, Any>()).rootDirectories.forEach {
                visitClassDirectory(it, classes, ignored, writer)
            }
        }
    }

    private fun visitClassDirectory(dir: Path, classes: ClassGraph, ignored: PackagePatterns, writer: PrintWriter) {
        Files.walkFileTree(dir, object : FileVisitor<Path> {
            private var seenManifest: Boolean = false

            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                writer.print(file.fileName.toString() + ": ")
                if (file.toString().endsWith(".class")) {
                    try {
                        var reader = ClassReader(Files.newInputStream(file))
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

                        writer.println("mapped class name: " + details.outputClassName)
                        val outputFile = File(classesDir, details.outputClassName + ".class")
                        outputFile.parentFile.mkdirs()
                        outputFile.writeBytes(classWriter.toByteArray())
                    } catch (exception: Exception) {
                        throw ClassAnalysisException("Could not transform class from " + file.toFile(), exception)
                    }

                } else if (file.toString().endsWith(".properties") && classes.unshadedPackages.matches(file.toString())) {
                    writer.println("include")
                    classes.addResource(ResourceDetails(file.toString(), file.toFile()))
                } else if (file.toString() == JarFile.MANIFEST_NAME && !seenManifest) {
                    seenManifest = true
                    classes.manifest = ResourceDetails(file.toString(), file.toFile())
                } else {
                    writer.println("skipped")
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                return FileVisitResult.TERMINATE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException): FileVisitResult {
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun writeJar(classes: ClassGraph, classesDir: File, jarFile: File, writer: PrintWriter) {
        try {
            writer.println()
            writer.println("CLASS GRAPH")
            writer.println()
            JarOutputStream(BufferedOutputStream(FileOutputStream(jarFile))).use { jarOutputStream ->
                if (classes.manifest != null) {
                    addJarEntry(classes.manifest!!.resourceName, classes.manifest!!.sourceFile, jarOutputStream)
                }
                val visited = LinkedHashSet<ClassDetails>()
                for (classDetails in classes.entryPoints) {
                    visitTree(classDetails, classesDir, jarOutputStream, writer, "- ", visited)
                }
                for (resource in classes.resources) {
                    addJarEntry(resource.resourceName, resource.sourceFile, jarOutputStream)
                }
                jarOutputStream.close()
            }
        } catch (exception: Exception) {
            throw ClassAnalysisException("Could not write shaded Jar " + jarFile, exception)
        }

    }

    private fun visitTree(classDetails: ClassDetails, classesDir: File, jarOutputStream: JarOutputStream, writer: PrintWriter, prefix: String, visited: MutableSet<ClassDetails>) {
        if (!visited.add(classDetails)) {
            return
        }
        if (classDetails.visited) {
            writer.println(prefix + classDetails.className)
            val fileName = classDetails.outputClassName + ".class"
            val classFile = File(classesDir, fileName)
            addJarEntry(fileName, classFile, jarOutputStream)
            for (dependency in classDetails.dependencies) {
                val childPrefix = "  " + prefix
                visitTree(dependency, classesDir, jarOutputStream, writer, childPrefix, visited)
            }
        } else {
            writer.println(prefix + classDetails.className + " (not included)")
        }
    }

    private fun addJarEntry(entryName: String, sourceFile: File, jarOutputStream: JarOutputStream) {
        jarOutputStream.putNextEntry(ZipEntry(entryName))
        BufferedInputStream(FileInputStream(sourceFile)).use { inputStream -> ByteStreams.copy(inputStream, jarOutputStream) }
        jarOutputStream.closeEntry()
    }

    private class ClassGraph(internal val keepPackages: PackagePatterns, internal val unshadedPackages: PackagePatterns, internal val ignorePackages: PackagePatterns, shadowPackage: String) {
        internal val classes: MutableMap<String, ClassDetails> = LinkedHashMap()
        internal val entryPoints: MutableSet<ClassDetails> = LinkedHashSet()
        internal val resources: MutableSet<ResourceDetails> = LinkedHashSet()
        internal var manifest: ResourceDetails? = null
        internal val shadowPackagePrefix = if (shadowPackage.isEmpty()) "" else shadowPackage.replace('.', '/') + "/"

        fun addResource(resource: ResourceDetails) {
            resources.add(resource)
        }

        operator fun get(className: String): ClassDetails {
            var classDetails: ClassDetails? = classes[className]
            if (classDetails == null) {
                classDetails = ClassDetails(className, if (unshadedPackages.matches(className)) className else shadowPackagePrefix + className)
                classes[className] = classDetails
                if (keepPackages.matches(className) && !ignorePackages.matches(className)) {
                    entryPoints.add(classDetails)
                }
            }
            return classDetails
        }
    }

    private class ResourceDetails(internal val resourceName: String, internal val sourceFile: File)

    private class ClassDetails(internal val className: String, internal val outputClassName: String) {
        internal var visited: Boolean = false
        internal val dependencies: MutableSet<ClassDetails> = LinkedHashSet()
    }

    private class PackagePatterns(prefixes: Set<String>) {
        private val prefixes = HashSet<String>()
        private val names = HashSet<String>()

        init {
            for (prefix in prefixes) {
                val internalName = prefix.replace('.', '/')
                this.names.add(internalName)
                this.prefixes.add(internalName + "/")
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

}
