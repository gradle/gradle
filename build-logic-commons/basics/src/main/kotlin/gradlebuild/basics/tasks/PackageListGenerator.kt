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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.CompileClasspath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.ErroringAction
import org.gradle.internal.IoActions
import org.gradle.internal.util.Trie
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream


/**
 * This task will generate the list of relocated packages into a file that will in turn be used when generating the runtime shaded jars. All we need is a list of packages that need to be relocated, so
 * we'll make sure to filter the list of packages before generating the file.
 *
 * It is assumed that the layout of the directories follow the JVM conventions. This allows us to effectively skip opening the class files to determine the real package name.
 */
@CacheableTask
abstract class PackageListGenerator : DefaultTask() {

    companion object {
        // Things we do not want to shade
        val DEFAULT_EXCLUDES: List<String> = listOf(
            "org/gradle",
            "java",
            "javax/annotation",
            "javax/inject",
            "javax/xml",
            "kotlin",
            "groovy",
            "groovyjarjarantlr",
            "net/rubygrapefruit",
            "org/codehaus/groovy",
            "org/apache/groovy",
            "org/apache/tools/ant",
            "org/apache/commons/logging",
            "org/jetbrains/annotations",
            "org/slf4j",
            "org/apache/log4j",
            "org/apache/xerces",
            "org/w3c/dom",
            "org/xml/sax",
            "sun/misc"
        )

        // Things we do want to shade despite being covered by excludes
        val DEFAULT_INCLUDES: List<String> = listOf(
            "org/gradle/fileevents"
        )

        @Throws(IOException::class)
        private
        fun openJarFile(file: Path): ZipInputStream {
            return ZipInputStream(Files.newInputStream(file))
        }
    }

    /**
     * Implementation code that can be unit-tested.
     *
     * <p>Visible for testing.</p>
     */
    class Implementation(private val excludes: List<String>, private val includes: List<String>) {
        fun collectPackages(files: Iterable<Path>): Trie {
            val builder = Trie.Builder()
            for (file in files) {
                if (Files.exists(file)) {
                    if (file.fileName.toString().endsWith(".jar")) {
                        processJarFile(file, builder)
                    } else {
                        processDirectory(file, builder)
                    }
                }
            }
            return builder.build()
        }

        private
        fun processDirectory(dir: Path, builder: Trie.Builder) {
            Files.walkFileTree(dir, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val zipEntry = ZipEntry(dir.relativize(file).toString())
                    processEntry(zipEntry, builder)
                    return FileVisitResult.CONTINUE
                }
            })
        }

        @Throws(IOException::class)
        private
        fun processJarFile(file: Path, builder: Trie.Builder) {
            IoActions.withResource(openJarFile(file), object : ErroringAction<ZipInputStream>() {
                @Throws(Exception::class)
                override fun doExecute(inputStream: ZipInputStream) {
                    var zipEntry = inputStream.nextEntry
                    while (zipEntry != null) {
                        processEntry(zipEntry, builder)
                        zipEntry = inputStream.nextEntry
                    }
                }
            })
        }

        @Throws(IOException::class)
        private
        fun processEntry(zipEntry: ZipEntry, builder: Trie.Builder) {
            val name = zipEntry.name
            if (name.endsWith(".class")) {
                processClassFile(zipEntry, builder)
            }
        }

        @Throws(IOException::class)
        private
        fun processClassFile(zipEntry: ZipEntry, builder: Trie.Builder) {
            val endIndex = zipEntry.name.lastIndexOf("/")
            if (endIndex > 0) {
                val packageName = zipEntry.name.substring(0, endIndex)
                if (shouldInclude(packageName, excludes, includes)) {
                    builder.addWord(packageName)
                }
            }
        }

        private
        fun shouldInclude(packageName: String, excludes: List<String>, includes: List<String>): Boolean {
            for (exclude in excludes) {
                if ("$packageName/".startsWith("$exclude/")) {
                    for (include in includes) {
                        if ("$packageName/".startsWith("$include/")) {
                            return true
                        }
                    }
                    return false
                }
            }
            return true
        }
    }

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:CompileClasspath
    abstract val classpath: ConfigurableFileCollection

    @get:Input
    abstract val excludes: ListProperty<String>

    @get:Input
    abstract val includes: ListProperty<String>

    init {
        excludes.convention(DEFAULT_EXCLUDES)
        includes.convention(DEFAULT_INCLUDES)
    }

    @TaskAction
    fun generate() {
        IoActions.writeTextFile(outputFile.asFile.get(), object : ErroringAction<BufferedWriter>() {
            @Throws(Exception::class)
            public
            override fun doExecute(bufferedWriter: BufferedWriter) {
                val packages = Implementation(excludes.get(), includes.get()).collectPackages(classpath.files.map(File::toPath))
                packages.dump(false, object : ErroringAction<String>() {
                    @Throws(Exception::class)
                    override fun doExecute(s: String) {
                        bufferedWriter.write(s)
                        bufferedWriter.write('\n'.code)
                    }
                })
            }
        })
    }
}
