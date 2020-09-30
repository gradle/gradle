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


import org.gradle.kotlin.dsl.concurrent.EventLoop
import org.gradle.kotlin.dsl.concurrent.future

import org.gradle.kotlin.dsl.tooling.models.EditorPosition
import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import org.gradle.tooling.BuildException

import com.google.common.annotations.VisibleForTesting

import java.io.File
import java.security.MessageDigest

import kotlin.script.dependencies.KotlinScriptExternalDependencies
import kotlin.script.dependencies.ScriptContents
import kotlin.script.dependencies.ScriptContents.Position
import kotlin.script.dependencies.ScriptDependenciesResolver
import kotlin.script.dependencies.ScriptDependenciesResolver.ReportSeverity

import kotlin.collections.contentEquals


private
typealias Report = (ReportSeverity, String, Position?) -> Unit


private
fun Report.warning(message: String, position: Position? = null) =
    invoke(ReportSeverity.WARNING, message, position)


private
fun Report.error(message: String, position: Position? = null) =
    invoke(ReportSeverity.ERROR, message, position)


private
fun Report.fatal(message: String, position: Position? = null) =
    invoke(ReportSeverity.FATAL, message, position)


private
fun Report.editorReport(editorReport: EditorReport) = editorReport.run {
    invoke(severity.toIdeSeverity(), message, position?.toIdePosition())
}


private
fun EditorReportSeverity.toIdeSeverity(): ReportSeverity =
    when (this) {
        EditorReportSeverity.WARNING -> ReportSeverity.WARNING
        EditorReportSeverity.ERROR -> ReportSeverity.ERROR
    }


private
fun EditorPosition.toIdePosition(): Position =
    Position(if (line == 0) 0 else line - 1, column)


class KotlinBuildScriptDependenciesResolver @VisibleForTesting constructor(

    private
    val logger: ResolverEventLogger

) : ScriptDependenciesResolver {

    @Suppress("unused")
    constructor() : this(DefaultResolverEventLogger)

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

        val cid = newCorrelationId()
        try {
            log(ResolutionRequest(cid, script.file, environment, previousDependencies))
            if (environment.isShortCircuitEnabled) {
                assembleDependenciesWithShortCircuit(
                    cid,
                    script,
                    environment!!,
                    report,
                    previousDependencies
                )
            } else {
                assembleDependenciesFrom(
                    cid,
                    script.file,
                    environment!!,
                    report,
                    previousDependencies
                )
            }
        } catch (e: BuildException) {
            log(ResolutionFailure(cid, script.file, e))
            if (previousDependencies == null) report.fatal(EditorMessages.buildConfigurationFailed)
            else report.warning(EditorMessages.buildConfigurationFailedUsingPrevious)
            previousDependencies
        } catch (e: Exception) {
            log(ResolutionFailure(cid, script.file, e))
            if (previousDependencies == null) report.fatal(EditorMessages.failure)
            else report.error(EditorMessages.failureUsingPrevious)
            previousDependencies
        }
    }

    private
    suspend fun assembleDependenciesWithShortCircuit(
        cid: String,
        script: ScriptContents,
        environment: Environment,
        report: Report,
        previousDependencies: KotlinScriptExternalDependencies?
    ): KotlinScriptExternalDependencies? =

        when (val action = ResolverCoordinator.selectNextActionFor(script, environment, previousDependencies)) {
            is ResolverAction.ReturnPrevious -> {
                log(ResolvedToPrevious(cid, script.file, previousDependencies))
                previousDependencies
            }
            is ResolverAction.RequestNew -> {
                assembleDependenciesFrom(
                    cid,
                    script.file,
                    environment,
                    report,
                    previousDependencies,
                    action.classPathBlocksHash
                )
            }
        }

    private
    suspend fun assembleDependenciesFrom(
        cid: String,
        scriptFile: File?,
        environment: Environment,
        report: Report,
        previousDependencies: KotlinScriptExternalDependencies?,
        classPathBlocksHash: ByteArray? = null
    ): KotlinScriptExternalDependencies? {

        val request = scriptModelRequestFrom(scriptFile, environment, cid)
        log(SubmittedModelRequest(cid, scriptFile, request))

        val response = DefaultKotlinBuildScriptModelRepository.scriptModelFor(request)
        if (response == null) {
            log(RequestCancelled(cid, scriptFile, request))
            return null
        }
        log(ReceivedModelResponse(cid, scriptFile, response))

        response.editorReports.forEach { editorReport ->
            report.editorReport(editorReport)
        }

        return when {
            response.exceptions.isEmpty() ->
                dependenciesFrom(request, response, classPathBlocksHash).also {
                    log(ResolvedDependencies(cid, scriptFile, it))
                }
            previousDependencies != null && previousDependencies.classpath.count() > response.classPath.size ->
                previousDependencies.also {
                    log(ResolvedToPreviousWithErrors(cid, scriptFile, previousDependencies, response.exceptions))
                }
            else ->
                dependenciesFrom(request, response, classPathBlocksHash).also {
                    log(ResolvedDependenciesWithErrors(cid, scriptFile, it, response.exceptions))
                }
        }
    }

    private
    fun log(event: ResolverEvent) = logger.log(event)

    private
    fun scriptModelRequestFrom(
        scriptFile: File?,
        environment: Environment,
        correlationId: String
    ): KotlinBuildScriptModelRequest =

        KotlinBuildScriptModelRequest(
            projectDir = environment.projectRoot,
            scriptFile = scriptFile,
            gradleInstallation = gradleInstallationFrom(environment),
            gradleUserHome = environment.gradleUserHome,
            javaHome = environment.gradleJavaHome,
            options = environment.gradleOptions,
            jvmOptions = environment.gradleJvmOptions,
            environmentVariables = environment.gradleEnvironmentVariables,
            correlationId = correlationId
        )

    private
    fun gradleInstallationFrom(environment: Environment): GradleInstallation =
        environment.gradleHome?.let(GradleInstallation::Local)
            ?: environment.gradleUri?.let(GradleInstallation::Remote)
            ?: environment.gradleVersion?.let(GradleInstallation::Version)
            ?: if ("embedded" == System.getProperty("org.gradle.integtest.executer")) {
                    GradleInstallation.Embedded
                } else {
                    GradleInstallation.Wrapper
                }

    private
    fun dependenciesFrom(
        request: KotlinBuildScriptModelRequest,
        response: KotlinBuildScriptModel,
        classPathBlocksHash: ByteArray?
    ) =
        KotlinBuildScriptDependencies(
            response.classPath,
            response.sourcePath,
            response.implicitImports,
            request.javaHome?.path,
            classPathBlocksHash
        )
}


/**
 * The resolver can either return the previous result
 * or request new dependency information from Gradle.
 */
internal
sealed class ResolverAction {
    object ReturnPrevious : ResolverAction()
    class RequestNew(val classPathBlocksHash: ByteArray?) : ResolverAction()
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

        val classPathBlocksHash = classPathBlocksHashFor(script, environment)
        if (sameClassPathBlocksHashAs(previousDependencies, classPathBlocksHash)) {
            return ResolverAction.ReturnPrevious
        }

        return ResolverAction.RequestNew(classPathBlocksHash)
    }

    private
    fun sameClassPathBlocksHashAs(previousDependencies: KotlinScriptExternalDependencies?, hash: ByteArray?) =
        hash?.let { nonNullHash -> classPathBlocksHashOf(previousDependencies)?.contentEquals(nonNullHash) }
            ?: false

    private
    fun classPathBlocksHashOf(previousDependencies: KotlinScriptExternalDependencies?) =
        (previousDependencies as? KotlinBuildScriptDependencies)?.classPathBlocksHash

    private
    fun classPathBlocksHashFor(script: ScriptContents, environment: Environment): ByteArray? {

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
                        updateWith("pluginManagement")
                        updateWith("initscript")
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
    override val javaHome: String? = null,
    val classPathBlocksHash: ByteArray?
) : KotlinScriptExternalDependencies


/**
 * Handles all incoming [KotlinBuildScriptModelRequest]s via a single [EventLoop] to avoid spawning
 * multiple competing Gradle daemons.
 */
private
object DefaultKotlinBuildScriptModelRepository : KotlinBuildScriptModelRepository()
