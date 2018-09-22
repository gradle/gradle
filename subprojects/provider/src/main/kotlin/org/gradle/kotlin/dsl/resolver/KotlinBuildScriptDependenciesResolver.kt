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

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import java.io.File
import java.net.URI

import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine

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
fun Report.warning(message: String, position: Position? = null) =
    invoke(ReportSeverity.WARNING, message, position)


private
fun Report.error(message: String, position: Position? = null) =
    invoke(ReportSeverity.ERROR, message, position)


class KotlinBuildScriptDependenciesResolver : ScriptDependenciesResolver {

    internal
    object Messages {
        const val failure = "Script dependencies resolution failed"
        const val failureUsingPrevious = "Script dependencies resolution failed, using previous dependencies"
        const val exceptions = "There were some errors during script dependencies resolution"
    }

    private
    val logger: ResolverEventLogger

    @Suppress("unused")
    constructor() {
        logger = DefaultResolverEventLogger
    }

    internal
    constructor(logger: ResolverEventLogger) {
        this.logger = logger
    }

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
            logger.log(ResolutionRequest(script.file, environment, previousDependencies))
            assembleDependenciesFrom(
                script.file,
                environment!!,
                report,
                previousDependencies)
        } catch (e: Exception) {
            logger.log(ResolutionFailure(script.file, e))
            if (previousDependencies == null) report.error(Messages.failure)
            else report.warning(Messages.failureUsingPrevious)
            previousDependencies
        }
    }

    private
    suspend fun assembleDependenciesFrom(
        scriptFile: File?,
        environment: Environment,
        report: Report,
        previousDependencies: KotlinScriptExternalDependencies?
    ): KotlinScriptExternalDependencies {

        val request = modelRequestFrom(scriptFile, environment)
        logger.log(SubmittedModelRequest(scriptFile, request))

        val response = RequestQueue.post(request)
        logger.log(ReceivedModelResponse(scriptFile, response))

        return when {
            response.exceptions.isEmpty() ->
                dependenciesFrom(response).also {
                    logger.log(ResolvedDependencies(scriptFile, it))
                }
            previousDependencies != null && previousDependencies.classpath.count() > response.classPath.size ->
                previousDependencies.also {
                    logger.log(ResolvedToPreviousWithErrors(scriptFile, previousDependencies, response.exceptions))
                    report.warning(Messages.exceptions)
                }
            else ->
                dependenciesFrom(response).also {
                    logger.log(ResolvedDependenciesWithErrors(scriptFile, it, response.exceptions))
                    report.warning(Messages.exceptions)
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
    fun dependenciesFrom(response: KotlinBuildScriptModel) =
        KotlinBuildScriptDependencies(
            response.classPath,
            response.sourcePath,
            response.implicitImports
        )
}


internal
class KotlinBuildScriptDependencies(
    override val classpath: Iterable<File>,
    override val sources: Iterable<File>,
    override val imports: Iterable<String>
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


private
typealias AsyncModelRequest = Pair<KotlinBuildScriptModelRequest, Continuation<KotlinBuildScriptModel>>


/**
 * Handles all incoming [KotlinBuildScriptModelRequest]s via a single [EventLoop] to avoid spawning
 * multiple competing Gradle daemons.
 */
private
object RequestQueue {

    suspend fun post(request: KotlinBuildScriptModelRequest) =
        suspendCoroutine<KotlinBuildScriptModel> { k ->
            require(eventLoop.accept(request to k))
        }

    private
    val eventLoop = EventLoop<AsyncModelRequest> { poll ->
        while (true) {
            val (request, k) = poll() ?: break
            try {
                handle(request, k)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private
    fun handle(request: KotlinBuildScriptModelRequest, k: Continuation<KotlinBuildScriptModel>) {
        k.resumeWith(
            runCatching {
                fetchKotlinBuildScriptModelFor(request)
            }
        )
    }
}
