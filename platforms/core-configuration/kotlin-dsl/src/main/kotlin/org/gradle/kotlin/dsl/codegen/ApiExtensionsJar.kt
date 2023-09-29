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

package org.gradle.kotlin.dsl.codegen

import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.kotlin.dsl.internal.sharedruntime.codegen.kotlinDslPackagePath
import org.gradle.kotlin.dsl.support.EmbeddedKotlinCompilerWarning
import org.gradle.kotlin.dsl.support.bytecode.GradleJvmVersion
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.zipTo
import java.io.File


internal
fun generateApiExtensionsJar(
    temporaryFileProvider: TemporaryFileProvider,
    outputFile: File,
    gradleJars: Collection<File>,
    gradleApiMetadataJar: File,
    onProgress: () -> Unit
) {
    ApiExtensionsJarGenerator(
        temporaryFileProvider,
        gradleJars,
        gradleApiMetadataJar,
        onProgress
    ).generate(outputFile)
}


private
class ApiExtensionsJarGenerator(
    val temporaryFileProvider: TemporaryFileProvider,
    val gradleJars: Collection<File>,
    val gradleApiMetadataJar: File,
    val onProgress: () -> Unit = {}
) {

    fun generate(outputFile: File) {
        val tempDir = tempDirFor(outputFile)
        compileExtensionsTo(tempDir)
        zipTo(outputFile, tempDir)
    }

    private
    fun tempDirFor(outputFile: File): File =
        temporaryFileProvider.createTemporaryDirectory(outputFile.nameWithoutExtension, outputFile.extension).apply {
            deleteOnExit()
        }

    private
    fun compileExtensionsTo(outputDir: File) {
        compileKotlinApiExtensionsTo(
            outputDir,
            sourceFilesFor(outputDir),
            classPath = gradleJars
        )
        onProgress()
    }

    private
    fun sourceFilesFor(outputDir: File) =
        gradleApiExtensionsSourceFilesFor(outputDir) +
            builtinPluginIdExtensionsSourceFileFor(outputDir)

    private
    fun gradleApiExtensionsSourceFilesFor(outputDir: File) =
        writeGradleApiKotlinDslExtensionsTo(outputDir, gradleJars, gradleApiMetadataJar).also {
            onProgress()
        }

    private
    fun builtinPluginIdExtensionsSourceFileFor(outputDir: File) =
        generatedSourceFile(outputDir, "BuiltinPluginIdExtensions.kt").apply {
            writeBuiltinPluginIdExtensionsTo(this, gradleJars)
            onProgress()
        }

    private
    fun generatedSourceFile(outputDir: File, fileName: String) =
        File(outputDir, sourceFileName(fileName)).apply {
            parentFile.mkdirs()
        }

    private
    fun sourceFileName(fileName: String) =
        "$packageDir/$fileName"

    private
    val packageDir = kotlinDslPackagePath
}


internal
fun compileKotlinApiExtensionsTo(
    outputDirectory: File,
    sourceFiles: Collection<File>,
    classPath: Collection<File>,
    logger: org.slf4j.Logger = loggerFor<ApiExtensionsJarGenerator>()
) {

    val success = compileToDirectory(
        outputDirectory,
        GradleJvmVersion.minimalJavaVersion,
        "gradle-api-extensions",
        sourceFiles,
        logger,
        classPath = classPath,
        onCompilerWarning = EmbeddedKotlinCompilerWarning.DEBUG
    )

    if (!success) {
        throw IllegalStateException(
            "Unable to compile Gradle Kotlin DSL API Extensions Jar\n" +
                "\tFrom:\n" +
                sourceFiles.joinToString("\n\t- ", prefix = "\t- ", postfix = "\n") +
                "\tSee compiler logs for details."
        )
    }
}
