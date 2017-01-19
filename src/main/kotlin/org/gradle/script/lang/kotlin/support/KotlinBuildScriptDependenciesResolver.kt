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

package org.gradle.script.lang.kotlin.support

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents
import org.jetbrains.kotlin.script.ScriptDependenciesResolver
import org.jetbrains.kotlin.script.asFuture

import java.io.File

import java.security.MessageDigest

import java.util.Arrays.equals
import java.util.concurrent.Future


typealias Environment = Map<String, Any?>


typealias ScriptSectionTokensProvider = (CharSequence, String) -> Sequence<CharSequence>


class KotlinBuildScriptDependenciesResolver : ScriptDependenciesResolver {

    internal var assembler: KotlinBuildScriptDependenciesAssembler = DefaultKotlinBuildScriptDependenciesAssembler

    override fun resolve(
        script: ScriptContents,
        environment: Environment?,
        report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
        previousDependencies: KotlinScriptExternalDependencies?): Future<KotlinScriptExternalDependencies?> {

        if (environment == null)
            return previousDependencies.asFuture()

        val buildscriptBlockHash = buildscriptBlockHashFor(script, environment)
        val dependencies = when {
            buildscriptBlockHash != null && sameBuildscriptBlockHashAs(previousDependencies, buildscriptBlockHash) ->
                null
            else ->
                assembler.assembleDependenciesFrom(environment, script.file, buildscriptBlockHash)
        } ?: previousDependencies

        return dependencies.asFuture()
    }

    private fun sameBuildscriptBlockHashAs(previousDependencies: KotlinScriptExternalDependencies?, hash: ByteArray) =
        buildscriptBlockHashOf(previousDependencies)?.let { equals(it, hash) } ?: false

    private fun buildscriptBlockHashOf(previousDependencies: KotlinScriptExternalDependencies?) =
        (previousDependencies as? KotlinBuildScriptDependencies)?.buildscriptBlockHash

    private fun buildscriptBlockHashFor(script: ScriptContents, environment: Environment): ByteArray? {
        @Suppress("unchecked_cast")
        val getScriptSectionTokens = environment["getScriptSectionTokens"] as? ScriptSectionTokensProvider
        return when {
            getScriptSectionTokens != null ->
                with(MessageDigest.getInstance("MD5")) {
                    val text = script.text ?: script.file?.readText()
                    text?.let { text ->
                        fun updateWith(section: String) =
                            getScriptSectionTokens(text, section).forEach {
                                update(it.toString().toByteArray())
                            }
                        updateWith("buildscript")
                        updateWith("plugins")
                    }
                    digest()
                }
            else -> null
        }
    }
}


internal
class KotlinBuildScriptDependencies(
    override val classpath: Iterable<File>,
    override val imports: Iterable<String>,
    override val sources: Iterable<File>,
    val buildscriptBlockHash: ByteArray?) : KotlinScriptExternalDependencies


internal
interface KotlinBuildScriptDependenciesAssembler {

    fun assembleDependenciesFrom(
        environment: Environment,
        scriptFile: File?,
        buildscriptBlockHash: ByteArray?): KotlinBuildScriptDependencies?
}


internal
object DefaultKotlinBuildScriptDependenciesAssembler : KotlinBuildScriptDependenciesAssembler {

    private val sourcePathProvider: SourcePathProvider = DefaultSourcePathProvider

    override fun assembleDependenciesFrom(
        environment: Environment,
        scriptFile: File?,
        buildscriptBlockHash: ByteArray?): KotlinBuildScriptDependencies? =

        modelRequestFrom(environment, scriptFile)?.let { request ->
            modelFor(request)?.let { response ->
                dependenciesFrom(buildscriptBlockHash, request, response)
            }
        }

    private fun modelRequestFrom(environment: Environment, scriptFile: File?): KotlinBuildScriptModelRequest? {
        val importedProjectRoot = environment["projectRoot"] as? File
        val gradleHome = environment["gradleHome"] as? File
        if (importedProjectRoot != null && gradleHome != null) {
            @Suppress("unchecked_cast")
            val gradleJvmOptions = environment["gradleJvmOptions"] as? List<String>
            val gradleJavaHome = (environment["gradleJavaHome"] as? String)?.let(::File)
            return KotlinBuildScriptModelRequest(
                projectDir = scriptFile?.let { projectRootOf(it, importedProjectRoot) } ?: importedProjectRoot,
                scriptFile = scriptFile,
                gradleInstallation = gradleHome,
                javaHome = gradleJavaHome,
                jvmOptions = gradleJvmOptions ?: emptyList())
        }
        return null
    }

    private fun dependenciesFrom(
        hash: ByteArray?,
        request: KotlinBuildScriptModelRequest,
        response: KotlinBuildScriptModel) =

        KotlinBuildScriptDependencies(
            response.classPath,
            ImplicitImports.list,
            sourcePathFor(request, response),
            hash)

    private fun sourcePathFor(request: KotlinBuildScriptModelRequest, response: KotlinBuildScriptModel) =
        sourcePathProvider.sourcePathFor(request, response)

    private fun modelFor(request: KotlinBuildScriptModelRequest) =
        fetchKotlinBuildScriptModelFor(request)
}

internal
fun projectRootOf(scriptFile: File, importedProjectRoot: File): File {

    fun isProjectRoot(dir: File) = File(dir, "settings.gradle").isFile

    tailrec fun test(dir: File): File =
        when {
            dir == importedProjectRoot -> importedProjectRoot
            isProjectRoot(dir) -> dir
            else -> {
                val parentDir = dir.parentFile
                when (parentDir) {
                    null, dir -> scriptFile.parentFile // external project
                    else -> test(parentDir)
                }
            }
        }

    return test(scriptFile.parentFile)
}
