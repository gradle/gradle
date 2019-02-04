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

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.support.normaliseLineSeparators

import java.io.File


@CacheableTask
open class GenerateScriptPluginAdapters : DefaultTask() {

    @get:OutputDirectory
    var outputDirectory = project.objects.directoryProperty()

    @get:Internal
    internal
    lateinit var plugins: List<ScriptPlugin>

    @get:InputFiles
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
fun ScriptPlugin.writeScriptPluginAdapterTo(outputDir: File) {

    val (packageDir, packageDeclaration) =
        packageName?.let { packageName ->
            packageDir(outputDir, packageName) to "package $packageName"
        } ?: outputDir to ""

    val outputFile =
        packageDir.resolve("$simplePluginAdapterClassName.kt")

    outputFile.writeText("""

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
                        .getDeclaredConstructor($targetType::class.java)
                        .newInstance(target)
                } catch (e: java.lang.reflect.InvocationTargetException) {
                    throw e.targetException
                }
            }
        }

    """.normaliseLineSeparators().replaceIndent().trim() + "\n")
}


private
fun packageDir(outputDir: File, packageName: String) =
    outputDir.mkdir(packageName.replace('.', '/'))


internal
inline fun <T> DirectoryProperty.withOutputDirectory(action: (File) -> T): T =
    asFile.get().let { outputDir ->
        outputDir.deleteRecursively()
        outputDir.mkdirs()
        action(outputDir)
    }


private
fun File.mkdir(relative: String) =
    resolve(relative).apply { mkdirs() }
