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

package org.gradle.kotlin.dsl.provider.plugins.precompiled.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.accessors.writeSourceCodeForPluginSpecBuildersFor
import org.gradle.kotlin.dsl.precompile.PrecompiledScriptDependenciesResolver.EnvironmentProperties.kotlinDslPluginSpecBuildersImplicitImports
import java.io.File


@CacheableTask
abstract class GenerateExternalPluginSpecBuilders : ClassPathSensitiveCodeGenerationTask(), SharedAccessorsPackageAware {

    @get:OutputDirectory
    abstract val metadataOutputDir: DirectoryProperty

    @TaskAction
    @Suppress("unused")
    internal
    fun generate() {
        val packageName = sharedAccessorsPackage
        sourceCodeOutputDir.withOutputDirectory { outputDir ->
            val packageDir = createPackageDirIn(outputDir, packageName)
            val outputFile = packageDir.resolve("PluginSpecBuilders.kt")
            writeSourceCodeForPluginSpecBuildersFor(
                classPath,
                outputFile,
                packageName
            )
        }
        metadataOutputDir.withOutputDirectory { outputDir ->
            outputDir.resolve(kotlinDslPluginSpecBuildersImplicitImports).writeText(
                "$packageName.*"
            )
        }
    }

    private
    fun createPackageDirIn(outputDir: File, packageName: String) =
        outputDir.resolve(packagePath(packageName)).apply { mkdirs() }

    private
    fun packagePath(packageName: String) =
        packageName.split('.').joinToString("/")
}
