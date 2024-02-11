/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.basics.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream


class PackageListGeneratorIntegrationTest {

    companion object {
        private
        val EXPECTED_PACKAGE_LIST = listOf("com/acme", "com/foo/internal", "javax/servlet/http")
        private
        val DEFAULT_EXCLUDES_FOR_TEST = listOf(
            "org/gradle",
            "java",
            "javax/xml",
            "javax/inject",
            "groovy",
            "groovyjarjarantlr",
            "net/rubygrapefruit",
            "org/codehaus/groovy",
            "org/apache/tools/ant",
            "org/apache/commons/logging",
            "org/slf4j",
            "org/apache/log4j",
            "org/apache/xerces",
            "org/w3c/dom",
            "org/xml/sax",
            "sun/misc"
        )
    }

    @TempDir
    lateinit var projectDir: Path

    private
    val implementation = PackageListGenerator.Implementation(DEFAULT_EXCLUDES_FOR_TEST)

    private
    fun getRelocatedPackages(files: Sequence<Path>): List<String> = mutableListOf<String>().apply {
        implementation.collectPackages(files.toList()).dump(false, this::add)
    }

    @Test
    fun `generates a curated list of package prefixes from directories`() {
        assertEquals(EXPECTED_PACKAGE_LIST, getRelocatedPackages(someClasses()))
    }

    @Test
    fun `generates a curated list of package prefixes from jars`() {
        assertEquals(EXPECTED_PACKAGE_LIST, getRelocatedPackages(aJar(someClasses())))
    }

    @Test
    fun `package list excludes default package`() {
        assertEquals(listOf<String>(), getRelocatedPackages(someClassesInDefaultPackage()))
        assertEquals(EXPECTED_PACKAGE_LIST, getRelocatedPackages(someClasses() + someClassesInDefaultPackage()))
    }

    @Test
    fun `package list excludes default package (in jar)`() {
        assertEquals(listOf<String>(), getRelocatedPackages(aJar(someClassesInDefaultPackage())))
        assertEquals(EXPECTED_PACKAGE_LIST, getRelocatedPackages(aJar(someClasses() + someClassesInDefaultPackage())))
    }

    private
    fun touchFile(path: Path) {
        Files.createDirectories(path.parent)
        Files.write(path, byteArrayOf())
    }

    private
    fun someClasses(): Sequence<Path> {
        val directory = projectDir.resolve("classes")

        touchFile(directory.resolve("com/acme/Foo.class"))
        touchFile(directory.resolve("com/acme/internal/FooInternal.class"))
        touchFile(directory.resolve("com/foo/internal/FooInternal.class"))
        touchFile(directory.resolve("javax/servlet/http/HttpServletRequest.class"))

        return sequenceOf(directory)
    }

    private
    fun someClassesInDefaultPackage(): Sequence<Path> {
        val directory = projectDir.resolve("classes-default")

        DEFAULT_EXCLUDES_FOR_TEST.forEachIndexed { i, pkg ->
            touchFile(directory.resolve("$pkg/Foo$i.class"))
        }

        return sequenceOf(directory)
    }

    private
    fun aJar(sourceFiles: Sequence<Path>): Sequence<Path> {
        val outputPath = projectDir.resolve("mylib.jar")
        Files.newOutputStream(outputPath).use { outputStream ->
            JarOutputStream(outputStream).use { jarStream ->
                sourceFiles.forEach { dir ->
                    check(Files.isDirectory(dir)) { "Must be a directory" }
                    Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            val entryName = dir.relativize(file).joinToString(separator = "/") { it.toString() }
                            jarStream.putNextEntry(JarEntry(entryName))
                            Files.copy(file, jarStream)
                            jarStream.closeEntry()
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            }
        }
        return sequenceOf(outputPath)
    }
}
