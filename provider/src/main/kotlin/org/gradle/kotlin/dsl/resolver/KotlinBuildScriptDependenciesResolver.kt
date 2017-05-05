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

package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.kotlin.dsl.concurrent.future

import org.gradle.tooling.ProgressListener

import java.io.File

import java.net.URI

import java.security.MessageDigest

import java.util.Arrays.equals

import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptDependenciesResolver


internal
typealias Environment = Map<String, Any?>


class KotlinBuildScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(
        script: ScriptContents,
        environment: Map<String, Any?>?,
        report: (ScriptDependenciesResolver.ReportSeverity, String, ScriptContents.Position?) -> Unit,
        previousDependencies: KotlinScriptExternalDependencies?) = future {

        try {
            val action = ResolverCoordinator.selectNextActionFor(script, environment, previousDependencies)
            when (action) {
                is ResolverAction.ReturnPrevious -> {
                    log(ResolvedToPrevious(script.file, environment, previousDependencies))
                    previousDependencies
                }
                is ResolverAction.RequestNew     -> {
                    assembleDependenciesFrom(script.file, environment!!, action.buildscriptBlockHash)
                }
            }
        } catch (e: Exception) {
            log(ResolutionFailure(script.file, e))
            previousDependencies
        }
    }

    private
    suspend fun assembleDependenciesFrom(
        scriptFile: File?,
        environment: Environment,
        buildscriptBlockHash: ByteArray?): KotlinScriptExternalDependencies {

        val request = modelRequestFrom(scriptFile, environment)
        log(SubmittedModelRequest(scriptFile, request))

        val response = submit(request, progressLogger(scriptFile))
        log(ReceivedModelResponse(scriptFile, response))

        val scriptDependencies = dependenciesFrom(response, buildscriptBlockHash)
        log(ResolvedDependencies(scriptFile, scriptDependencies))

        return scriptDependencies
    }

    private
    fun modelRequestFrom(scriptFile: File?, environment: Environment): KotlinBuildScriptModelRequest {

        @Suppress("unchecked_cast")
        fun stringList(key: String) =
            (environment[key] as? List<String>) ?: emptyList()

        fun path(key: String) =
            (environment[key] as? String)?.let(::File)

        val importedProjectRoot = environment["projectRoot"] as File
        return KotlinBuildScriptModelRequest(
            projectDir = scriptFile?.let { projectRootOf(it, importedProjectRoot) } ?: importedProjectRoot,
            scriptFile = scriptFile,
            gradleInstallation = gradleInstallationFrom(environment),
            gradleUserHome = path("gradleUserHome"),
            javaHome = path("gradleJavaHome"),
            options = stringList("gradleOptions"),
            jvmOptions = stringList("gradleJvmOptions"))
    }

    private
    suspend fun submit(request: KotlinBuildScriptModelRequest, progressListener: ProgressListener): KotlinBuildScriptModel =
        fetchKotlinBuildScriptModelFor(request) {
            addProgressListener(progressListener)
        }

    private
    fun progressLogger(scriptFile: File?) =
        ProgressListener { log(ResolutionProgress(scriptFile, it.description)) }

    private
    fun gradleInstallationFrom(environment: Environment): GradleInstallation =
        (environment["gradleHome"] as? File)?.let(GradleInstallation::Local)
            ?: (environment["gradleUri"] as? URI)?.let(GradleInstallation::Remote)
            ?: (environment["gradleVersion"] as? String)?.let(GradleInstallation::Version)
            ?: GradleInstallation.Wrapper

    private
    fun dependenciesFrom(
        response: KotlinBuildScriptModel,
        hash: ByteArray?) =

        KotlinBuildScriptDependencies(
            response.classPath,
            response.sourcePath,
            response.implicitImports,
            hash)

    private
    fun log(event: ResolverEvent) =
        ResolverEventLogger.log(event)
}


/**
 * The resolver can either return the previous result
 * or request new dependency information from Gradle.
 */
internal
sealed class ResolverAction {
    object ReturnPrevious : ResolverAction()
    class RequestNew(val buildscriptBlockHash: ByteArray?) : ResolverAction()
}


internal
object ResolverCoordinator {

    /**
     * Decides which action the resolver should take based on the given [script] and [environment].
     */
    fun selectNextActionFor(
        script: ScriptContents,
        environment: Environment?,
        previousDependencies: KotlinScriptExternalDependencies?): ResolverAction =

        when (environment) {
            null -> ResolverAction.ReturnPrevious
            else -> {
                val buildscriptBlockHash = buildscriptBlockHashFor(script, environment)
                if (sameBuildscriptBlockHashAs(previousDependencies, buildscriptBlockHash)) {
                    ResolverAction.ReturnPrevious
                } else {
                    ResolverAction.RequestNew(buildscriptBlockHash)
                }
            }
        }

    private
    fun sameBuildscriptBlockHashAs(previousDependencies: KotlinScriptExternalDependencies?, hash: ByteArray?) =
        hash?.let { nonNullHash -> buildscriptBlockHashOf(previousDependencies)?.let { equals(it, nonNullHash) } } ?: false

    private
    fun buildscriptBlockHashOf(previousDependencies: KotlinScriptExternalDependencies?) =
        (previousDependencies as? KotlinBuildScriptDependencies)?.buildscriptBlockHash

    private
    fun buildscriptBlockHashFor(script: ScriptContents, environment: Environment): ByteArray? {

        @Suppress("unchecked_cast")
        val getScriptSectionTokens = environment["getScriptSectionTokens"] as? ScriptSectionTokensProvider
        return when (getScriptSectionTokens) {
            null -> null
            else ->
                MessageDigest.getInstance("MD5").run {
                    val text = script.text ?: script.file?.readText()
                    text?.let { nonNullText ->
                        fun updateWith(section: String) =
                            getScriptSectionTokens(nonNullText, section).forEach {
                                update(it.toString().toByteArray())
                            }
                        updateWith("buildscript")
                        updateWith("plugins")
                    }
                    digest()
                }
        }
    }
}


internal
typealias ScriptSectionTokensProvider = (CharSequence, String) -> Sequence<CharSequence>


internal
class KotlinBuildScriptDependencies(
    override val classpath: Iterable<File>,
    override val sources: Iterable<File>,
    override val imports: Iterable<String>,
    val buildscriptBlockHash: ByteArray?) : KotlinScriptExternalDependencies


internal
fun projectRootOf(scriptFile: File, importedProjectRoot: File): File {

    // TODO:pm remove hardcoded reference to settings.gradle
    fun isProjectRoot(dir: File) = File(dir, "settings.gradle").isFile

    tailrec fun test(dir: File): File =
        when {
            dir == importedProjectRoot -> importedProjectRoot
            isProjectRoot(dir)         -> dir
            else                       -> {
                val parentDir = dir.parentFile
                when (parentDir) {
                    null, dir -> scriptFile.parentFile // external project
                    else      -> test(parentDir)
                }
            }
        }

    return test(scriptFile.parentFile)
}
