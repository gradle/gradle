/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.kotlin.dsl.internal.sharedruntime.support

import java.io.Closeable
import java.io.File
import java.util.jar.JarFile


private
typealias ClassBytesSupplier = () -> ByteArray


private
typealias ClassBytesIndex = (String) -> ClassBytesSupplier?


/**
 * Repository providing access to class bytes by Kotlin source names.
 *
 * Follows the one directory per package name segment convention.
 * Keeps JAR files open for fast lookup, must be closed.
 *
 * Always include the current JVM platform loader for which no JAR file can be held open.
 */
class ClassBytesRepository(
    private val classPathFiles: List<File>,
    classPathDependencies: List<File> = emptyList(),
    platformClassLoader: ClassLoader
) : Closeable {

    private
    val openJars = mutableMapOf<File, JarFile>()

    private
    val classBytesIndex = (classPathFiles + classPathDependencies + platformClassLoader).map { classBytesIndexFor(it) }

    /**
     * Class file bytes for Kotlin source name, if found.
     */
    fun classBytesFor(sourceName: String): ByteArray? =
        classBytesSupplierForSourceName(sourceName)?.let { it() }

    /**
     * All found class files bytes by Kotlin source name.
     */
    fun allClassesBytesBySourceName(): Sequence<Pair<String, ClassBytesSupplier>> =
        classPathFiles.asSequence()
            .flatMap { sourceNamesFrom(it) }
            .mapNotNull { sourceName ->
                classBytesSupplierForSourceName(sourceName)?.let { sourceName to it }
            }

    private
    fun classBytesSupplierForSourceName(sourceName: String): ClassBytesSupplier? =
        classFilePathCandidatesFor(sourceName)
            .mapNotNull(::classBytesSupplierForFilePath)
            .firstOrNull()

    private
    fun classBytesSupplierForFilePath(classFilePath: String): ClassBytesSupplier? =
        classBytesIndex.asSequence().mapNotNull { it(classFilePath) }.firstOrNull()

    private
    fun sourceNamesFrom(entry: File): Sequence<String> =
        when {
            entry.isClassPathArchive -> sourceNamesFromJar(entry)
            entry.isDirectory -> sourceNamesFromDir(entry)
            else -> emptySequence()
        }

    private
    fun sourceNamesFromJar(jar: File): Sequence<String> =
        openJarFile(jar).run {
            entries().asSequence()
                .filter { it.name.isClassFilePath }
                .map { kotlinSourceNameOf(it.name) }
        }

    private
    fun sourceNamesFromDir(dir: File): Sequence<String> =
        dir.walkTopDown()
            .filter { it.name.isClassFilePath }
            .map { kotlinSourceNameOf(normaliseFileSeparators(it.relativeTo(dir).path)) }

    private
    fun classBytesIndexFor(entry: Any): ClassBytesIndex =
        when (entry) {
            is File -> when {
                entry.isClassPathArchive -> jarClassBytesIndexFor(entry)
                entry.isDirectory -> directoryClassBytesIndexFor(entry)
                else -> { _ -> null }
            }

            is ClassLoader -> loaderClassBytesIndexFor(entry)

            else -> { _ -> null }
        }

    private
    fun jarClassBytesIndexFor(jar: File): ClassBytesIndex = { classFilePath ->
        openJarFile(jar).run {
            getJarEntry(classFilePath)?.let { jarEntry ->
                { getInputStream(jarEntry).use { jarInput -> jarInput.readBytes() } }
            }
        }
    }

    private
    fun directoryClassBytesIndexFor(dir: File): ClassBytesIndex = { classFilePath ->
        dir.resolve(classFilePath).takeIf { it.isFile }?.let { classFile -> { classFile.readBytes() } }
    }

    private
    fun loaderClassBytesIndexFor(loader: ClassLoader): ClassBytesIndex = { classFilePath ->
        loader.getResource(classFilePath)?.let {
            { loader.getResourceAsStream(classFilePath).use { it!!.readBytes() } }
        }
    }

    private
    fun openJarFile(file: File) =
        openJars.computeIfAbsent(file, ::JarFile)

    override fun close() {
        openJars.values.forEach(JarFile::close)
    }
}


/**
 * See https://docs.oracle.com/javase/8/docs/technotes/tools/findingclasses.html#userclass
 */
private
val File.isClassPathArchive
    get() = extension.run { equals("jar", ignoreCase = true) || equals("zip", ignoreCase = true) }


private
val String.isClassFilePath
    get() = endsWith(classFileExtension)
        && !endsWith("package-info$classFileExtension")
        && !matches(compilerGeneratedClassFilePath)


private
const val classFileExtension = ".class"


private
val compilerGeneratedClassFilePath = Regex(".*\\$\\d+\\.class$")


private
val slashOrDollar = Regex("[/$]")


// visible for testing
fun kotlinSourceNameOf(classFilePath: String): String =
    classFilePath
        .removeSuffix(classFileExtension)
        .removeSuffix("Kt")
        .replace(slashOrDollar, ".")


// visible for testing
fun classFilePathCandidatesFor(sourceName: String): Sequence<String> =
    sourceName.replace(".", "/").let { path ->
        candidateClassFiles(path) + nestedClassFilePathCandidatesFor(path)
    }


private
fun nestedClassFilePathCandidatesFor(path: String): Sequence<String> =
    generateSequence({ nestedClassNameFor(path) }, ::nestedClassNameFor)
        .flatMap(::candidateClassFiles)


private
fun candidateClassFiles(path: String) =
    sequenceOf("$path$classFileExtension", "${path}Kt$classFileExtension")


private
fun nestedClassNameFor(path: String) = path.run {
    lastIndexOf('/').takeIf { it > 0 }?.let { index ->
        substring(0, index) + '$' + substring(index + 1)
    }
}


private
fun normaliseFileSeparators(path: String): String =
    path.replace(File.separatorChar, '/')
