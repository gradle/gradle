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

import java.io.File

import java.net.URI

import java.security.MessageDigest

import java.util.Arrays.equals

import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptContents.Position
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity


internal
typealias Environment = Map<String, Any?>


private
typealias Report = (ReportSeverity, String, Position?) -> Unit


private
fun Report.warning(message: String) =
    invoke(ReportSeverity.WARNING, message, null)


private
fun Report.error(message: String) =
    invoke(ReportSeverity.ERROR, message, null)


class KotlinBuildScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(
        script: ScriptContents,
        environment: Map<String, Any?>?,
        /**
         * Shows a message in the IDE.
         *
         * To report whole file errors (e.g. failure to query for dependencies), one can just pass a
         * null position so the error/warning will be shown in the top panel of the editor
         *
         * Also there is a FATAL Severity - in this case the highlighting of the file will be
         * switched off (may be it is useful for some errors).
         */
        report: (ReportSeverity, String, Position?) -> Unit,
        previousDependencies: KotlinScriptExternalDependencies?
    ) = future {

        try {
            log(ResolutionRequest(script.file, environment, previousDependencies))
            val action = ResolverCoordinator.selectNextActionFor(script, environment, previousDependencies)
            when (action) {
                is ResolverAction.Return -> {
                    action.dependencies
                }
                is ResolverAction.ReturnPrevious -> {
                    log(ResolvedToPrevious(script.file, previousDependencies))
                    previousDependencies
                }
                is ResolverAction.RequestNew -> {
                    assembleDependenciesFrom(
                        script.file,
                        environment!!,
                        report,
                        previousDependencies,
                        action.buildscriptBlockHash)
                }
            }
        } catch (e: Exception) {
            if (previousDependencies == null) report.error("Script dependencies resolution failed")
            else report.warning("Script dependencies resolution failed, using previous dependencies")
            log(ResolutionFailure(script.file, e))
            previousDependencies
        }
    }

    private
    suspend fun assembleDependenciesFrom(
        scriptFile: File?,
        environment: Environment,
        report: Report,
        previousDependencies: KotlinScriptExternalDependencies?,
        buildscriptBlockHash: ByteArray?
    ): KotlinScriptExternalDependencies {

        val request = modelRequestFrom(scriptFile, environment)
        log(SubmittedModelRequest(scriptFile, request))

        val response = fetchKotlinBuildScriptModelFor(request)
        log(ReceivedModelResponse(scriptFile, response))

        return when {
            response.exceptions.isEmpty() ->
                dependenciesFrom(response, buildscriptBlockHash).also {
                    log(ResolvedDependencies(scriptFile, it))
                }
            previousDependencies != null && previousDependencies.classpath.count() > response.classPath.size ->
                previousDependencies.also {
                    report.warning("There were some errors during script dependencies resolution, using previous dependencies")
                    log(ResolvedToPreviousWithErrors(scriptFile, previousDependencies, response.exceptions))
                }
            else ->
                dependenciesFrom(response, buildscriptBlockHash).also {
                    report.warning("There were some errors during script dependencies resolution, some dependencies might be missing")
                    log(ResolvedDependenciesWithErrors(scriptFile, it, response.exceptions))
                }
        }
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
    fun gradleInstallationFrom(environment: Environment): GradleInstallation =
        (environment["gradleHome"] as? File)?.let(GradleInstallation::Local)
            ?: (environment["gradleUri"] as? URI)?.let(GradleInstallation::Remote)
            ?: (environment["gradleVersion"] as? String)?.let(GradleInstallation::Version)
            ?: GradleInstallation.Wrapper

    private
    fun dependenciesFrom(
        response: KotlinBuildScriptModel,
        hash: ByteArray?
    ) =

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
    class Return(val dependencies: KotlinScriptExternalDependencies?) : ResolverAction()
}


internal
object ResolverCoordinator {

    /**
     * Decides which action the resolver should take based on the given [script] and [environment].
     */
    fun selectNextActionFor(
        script: ScriptContents,
        environment: Environment?,
        previousDependencies: KotlinScriptExternalDependencies?
    ): ResolverAction {

        if (environment == null) {
            return ResolverAction.ReturnPrevious
        }

        val buildscriptBlockHash = buildscriptBlockHashFor(script, environment)
        if (sameBuildscriptBlockHashAs(previousDependencies, buildscriptBlockHash)) {
            return ResolverAction.ReturnPrevious
        }

        return ResolverAction.RequestNew(buildscriptBlockHash)
    }

    private
    fun sameBuildscriptBlockHashAs(previousDependencies: KotlinScriptExternalDependencies?, hash: ByteArray?) =
        hash?.let { nonNullHash -> buildscriptBlockHashOf(previousDependencies)?.let { equals(it, nonNullHash) } }
            ?: false

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
    val buildscriptBlockHash: ByteArray?
) : KotlinScriptExternalDependencies


internal
fun projectRootOf(scriptFile: File, importedProjectRoot: File): File {

    // TODO remove hardcoded reference to settings.gradle once there's a public TAPI client api for that
    fun isProjectRoot(dir: File) =
        File(dir, "settings.gradle.kts").isFile
            || File(dir, "settings.gradle").isFile
            || dir.name == "buildSrc"

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
