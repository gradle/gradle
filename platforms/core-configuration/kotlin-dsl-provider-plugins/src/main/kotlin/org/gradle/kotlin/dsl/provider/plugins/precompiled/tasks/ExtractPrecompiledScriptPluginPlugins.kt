/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.execution.Program
import org.gradle.kotlin.dsl.execution.ProgramKind
import org.gradle.kotlin.dsl.execution.ProgramParser
import org.gradle.kotlin.dsl.execution.ProgramSource
import org.gradle.kotlin.dsl.execution.ProgramTarget

import org.gradle.kotlin.dsl.provider.plugins.precompiled.PrecompiledScriptPlugin
import org.gradle.kotlin.dsl.provider.plugins.precompiled.scriptPluginFilesOf

import org.gradle.kotlin.dsl.support.KotlinScriptType

import java.io.File


/**
 * Extracts the `plugins` block of each precompiled [Project] script plugin
 * and writes it to a file with the same name under [outputDir].
 */
@CacheableTask
abstract class ExtractPrecompiledScriptPluginPlugins : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

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
    fun extract() {
        outputDir.withOutputDirectory {
            extractPrecompiledScriptPluginPluginsTo(it, plugins)
        }
    }
}


internal
fun extractPrecompiledScriptPluginPluginsTo(outputDir: File, scriptPlugins: List<PrecompiledScriptPlugin>) {
    for (scriptPlugin in scriptPlugins) {
        pluginsBlockOf(scriptPlugin)?.let {
            writePluginsBlockTo(outputDir, scriptPlugin, it)
        }
    }
}


private
fun pluginsBlockOf(scriptPlugin: PrecompiledScriptPlugin): Program.Plugins? =
    when (scriptPlugin.scriptType) {
        KotlinScriptType.PROJECT -> pluginsBlockOf(parse(scriptPlugin))
        else -> null
    }


private
fun pluginsBlockOf(program: Program): Program.Plugins? =
    when (program) {
        is Program.Plugins -> program
        is Program.Stage1Sequence -> program.plugins
        is Program.Staged -> pluginsBlockOf(program.stage1)
        else -> null
    }


private
fun parse(scriptPlugin: PrecompiledScriptPlugin): Program = ProgramParser.parse(
    ProgramSource(scriptPlugin.scriptFileName, scriptPlugin.scriptText),
    ProgramKind.TopLevel,
    ProgramTarget.Project
).document


private
fun writePluginsBlockTo(
    outputDir: File,
    scriptPlugin: PrecompiledScriptPlugin,
    program: Program.Plugins
) {
    outputFileFor(scriptPlugin, outputDir).writeText(
        packageDeclarationOf(scriptPlugin) + lineNumberPreservingTextOf(program)
    )
}


private
fun outputFileFor(scriptPlugin: PrecompiledScriptPlugin, outputDir: File) =
    packageDirFor(scriptPlugin, outputDir).resolve(scriptPlugin.scriptFileName)


private
fun packageDirFor(scriptPlugin: PrecompiledScriptPlugin, outputDir: File): File =
    scriptPlugin.packageName?.run {
        outputDir.resolve(replace('.', '/')).apply {
            mkdirs()
        }
    } ?: outputDir


private
fun packageDeclarationOf(scriptPlugin: PrecompiledScriptPlugin): String =
    scriptPlugin.packageName?.let {
        "package $it; "
    } ?: ""


private
fun lineNumberPreservingTextOf(program: Program.Plugins): String = program.fragment.run {
    source.map { it.subText(0..range.endInclusive).preserve(range) }
}.text
