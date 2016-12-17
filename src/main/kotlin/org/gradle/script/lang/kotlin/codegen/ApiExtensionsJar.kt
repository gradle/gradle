/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.script.lang.kotlin.codegen

import org.gradle.script.lang.kotlin.loggerFor
import org.gradle.script.lang.kotlin.support.compileToDirectory
import org.gradle.script.lang.kotlin.support.zipTo

import java.io.File


internal
fun generateApiExtensionsJar(outputFile: File, apiJar: File, gradleJars: Collection<File>, onProgress: () -> Unit) {
    ApiExtensionsJarGenerator(onProgress = onProgress).generate(outputFile, apiJar, gradleJars)
}


internal
interface KotlinFileCompiler {
    fun compileToDirectory(outputDirectory: File, sourceFiles: Collection<File>, classPath: Collection<File>)
}


internal
class ApiExtensionsJarGenerator(
    val compiler: KotlinFileCompiler = StandardKotlinFileCompiler,
    val onProgress: () -> Unit = {}) {

    fun generate(outputFile: File, inputApiJar: File, gradleJars: Collection<File> = emptyList()) {
        val tempDir = tempDirFor(outputFile)
        compileExtensionsTo(tempDir, inputApiJar, gradleJars)
        zipTo(outputFile, tempDir)
    }

    private fun tempDirFor(outputFile: File): File =
        createTempDir(outputFile.nameWithoutExtension, outputFile.extension).apply {
            deleteOnExit()
        }

    private fun compileExtensionsTo(outputDir: File, inputApiJar: File, gradleJars: Collection<File>) {
        compiler.compileToDirectory(
            outputDir,
            listOf(
                builtinPluginIdExtensionsSourceFileFor(gradleJars, outputDir),
                actionExtensionsSourceFileFor(inputApiJar, outputDir)),
            classPath = gradleJars)
    }

    private fun builtinPluginIdExtensionsSourceFileFor(gradleJars: Iterable<File>, outputDir: File) =
        generatedSourceFile(outputDir, "BuiltinPluginIdExtensions.kt").apply {
            writeBuiltinPluginIdExtensionsTo(this, gradleJars)
        }

    private fun actionExtensionsSourceFileFor(inputApiJar: File, outputDir: File) =
        generatedSourceFile(outputDir, "ActionExtensions.kt").apply {
            writeActionExtensionsTo(this, inputApiJar)
        }

    private fun generatedSourceFile(outputDir: File, fileName: String) =
        File(outputDir, sourceFileName(fileName)).apply {
            parentFile.mkdirs()
        }

    private fun sourceFileName(fileName: String) =
        packageDir + "/" + fileName

    private val packageDir = packageName.replace('.', '/')

    private fun writeActionExtensionsTo(kotlinFile: File, inputApiJar: File) {
        kotlinFile.bufferedWriter().use { writer ->
            val extensionWriter = ActionExtensionWriter(writer, docProvider())
            forEachZipEntryIn(inputApiJar) {
                if (isApiClassEntry()) {
                    val classNode = classNodeFor(zipInputStream)
                    extensionWriter.writeExtensionsFor(classNode)
                }
                onProgress()
            }
        }
    }

    private fun docProvider() =
        MarkdownKDocProvider.fromResource("/doc/ActionExtensions.md")
}


internal
object StandardKotlinFileCompiler : KotlinFileCompiler {
    override fun compileToDirectory(outputDirectory: File, sourceFiles: Collection<File>, classPath: Collection<File>) {
        compileToDirectory(
            outputDirectory,
            sourceFiles,
            loggerFor<StandardKotlinFileCompiler>(),
            classPath = classPath)
    }
}
