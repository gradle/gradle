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

package org.gradle.kotlin.dsl.plugins.precompiled

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.accessors.writeSourceCodeForPluginSpecBuildersFor


@CacheableTask
open class GeneratePluginSpecBuilders : ClassPathSensitiveCodeGenerationTask() {

    @TaskAction
    @Suppress("unused")
    internal
    fun generate() =
        sourceCodeOutputDir.withOutputDirectory { outputDir ->
            val packageDir = outputDir.resolve(packageName.split('.').joinToString("/")).apply {
                mkdirs()
            }
            val outputFile = packageDir.resolve("PluginSpecBuildersFor$$classPathHash.kt")
            writeSourceCodeForPluginSpecBuildersFor(
                classPath,
                outputFile,
                packageName = kotlinPackageName
            )
        }

    @get:Internal
    internal
    val kotlinPackageName by lazy {
        kotlinPackageNameFor(packageName)
    }

    // TODO: move to a package name derived from the classpath hash
    // "gradle-kotlin-dsl.plugin-spec-builders.$$classPathHash"
    private
    val packageName
        get() = "org.gradle.kotlin.dsl"
}
