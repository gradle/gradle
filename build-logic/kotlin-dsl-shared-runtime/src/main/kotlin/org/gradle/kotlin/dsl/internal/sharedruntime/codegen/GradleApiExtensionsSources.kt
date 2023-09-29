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

package org.gradle.kotlin.dsl.internal.sharedruntime.codegen

import java.io.File


fun generateGradleApiExtensionsSources(
    outputDir: File,
    gradleJars: Collection<File>,
    gradleApiMetadataJar: File,
) {
    GradleApiExtensionsSourcesGenerator(
        gradleJars,
        gradleApiMetadataJar,
    ).generate(outputDir)
}


private
class GradleApiExtensionsSourcesGenerator(
    val gradleJars: Collection<File>,
    val gradleApiMetadataJar: File,
) {

    fun generate(outputDir: File) {
        builtinPluginIdExtensionsSourceFileFor(outputDir)
        gradleApiExtensionsSourceFilesFor(outputDir)
    }

    private
    fun gradleApiExtensionsSourceFilesFor(outputDir: File) =
        writeGradleApiKotlinDslExtensionsTo(outputDir, gradleJars, gradleApiMetadataJar)

    private
    fun builtinPluginIdExtensionsSourceFileFor(outputDir: File) =
        generatedSourceFile(outputDir, "BuiltinPluginIdExtensions.kt").apply {
            writeBuiltinPluginIdExtensionsTo(this, gradleJars)
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
