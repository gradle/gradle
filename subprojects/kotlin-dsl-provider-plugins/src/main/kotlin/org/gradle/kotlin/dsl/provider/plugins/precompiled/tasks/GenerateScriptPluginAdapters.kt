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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptPlugin
import org.gradle.kotlin.dsl.provider.plugins.precompiled.scriptPluginFilesOf
import java.io.File


@CacheableTask
abstract class GenerateScriptPluginAdapters : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    internal
    lateinit var plugins: List<PrecompiledScriptPlugin>

    @get:InputFiles
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @Suppress("unused")
    internal
    val scriptFiles: Set<File>
        get() = scriptPluginFilesOf(plugins)

    @TaskAction
    @Suppress("unused")
    internal
    fun generate() =
        outputDirectory.withOutputDirectory { outputDir ->
            for (scriptPlugin in plugins) {
                scriptPlugin.writeScriptPluginAdapterTo(outputDir)
            }
        }
}


internal
fun PrecompiledScriptPlugin.writeScriptPluginAdapterTo(outputDir: File) {

    val (packageDir, packageDeclaration) =
        packageName?.let { packageName ->
            packageDir(outputDir, packageName) to "package $packageName"
        } ?: outputDir to ""

    val outputFile =
        packageDir.resolve("$simplePluginAdapterClassName.kt")

    outputFile.writeText(
        """

        $packageDeclaration


        /**
         * Precompiled [$scriptFileName][$compiledScriptTypeName] script plugin.
         *
         * @see $compiledScriptTypeName
         */
        class $simplePluginAdapterClassName : org.gradle.api.Plugin<$targetType> {
            override fun apply(target: $targetType) {
                try {
                    Class
                        .forName("$compiledScriptTypeName")
                        .getDeclaredConstructor($targetType::class.java, $targetType::class.java)
                        .newInstance(target, target)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        }

        """.trimIndent().trim() + "\n"
    )
}


private
fun packageDir(outputDir: File, packageName: String) =
    outputDir.mkdir(packageName.replace('.', '/'))


private
fun File.mkdir(relative: String) =
    resolve(relative).apply { mkdirs() }
