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

package org.gradle.script.lang.kotlin.resolver

import org.gradle.internal.os.OperatingSystem

import org.gradle.script.lang.kotlin.concurrent.future
import org.gradle.script.lang.kotlin.support.ImplicitImports
import org.gradle.script.lang.kotlin.support.userHome

import org.gradle.tooling.ProgressListener

import org.jetbrains.kotlin.script.KotlinScriptExternalDependencies
import org.jetbrains.kotlin.script.ScriptContents
import org.jetbrains.kotlin.script.ScriptDependenciesResolver

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

import java.net.URI

import java.security.MessageDigest

import java.text.SimpleDateFormat

import java.util.*
import java.util.Arrays.equals
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

import kotlin.concurrent.thread


typealias Environment = Map<String, Any?>


class KotlinBuildScriptDependenciesResolver : ScriptDependenciesResolver {

    override fun resolve(
        script: ScriptContents,
        environment: Environment?,
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
            throw e
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

        val scriptDependencies = dependenciesFrom(request, response, buildscriptBlockHash)
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
        request: KotlinBuildScriptModelRequest,
        response: KotlinBuildScriptModel,
        hash: ByteArray?) =

        KotlinBuildScriptDependencies(
            response.classPath,
            ImplicitImports.list,
            sourcePathFor(request, response),
            hash)

    private
    fun sourcePathFor(request: KotlinBuildScriptModelRequest, response: KotlinBuildScriptModel) =
        SourcePathProvider.sourcePathFor(request, response)

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
        hash?.let { hash -> buildscriptBlockHashOf(previousDependencies)?.let { equals(it, hash) } } ?: false

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
        }
    }
}


typealias ScriptSectionTokensProvider = (CharSequence, String) -> Sequence<CharSequence>


internal
class KotlinBuildScriptDependencies(
    override val classpath: Iterable<File>,
    override val imports: Iterable<String>,
    override val sources: Iterable<File>,
    val buildscriptBlockHash: ByteArray?) : KotlinScriptExternalDependencies {

    override fun toString(): String = "${super.toString()}(classpath=$classpath, sources=$sources)"
}


internal
fun projectRootOf(scriptFile: File, importedProjectRoot: File): File {

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


private
object ResolverEventLogger {

    fun log(event: ResolverEvent) {
        require(consumer.isAlive)
        q.offer(now() to event, 50, TimeUnit.MILLISECONDS)
    }

    private
    val q = ArrayBlockingQueue<Pair<Date, ResolverEvent>>(64)

    private
    val consumer = thread { // TODO: Don't leak this thread

        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

        bufferedWriter().use { writer ->

            fun write(timestamp: Date, e: ResolverEvent) {
                writer.write("${format.format(timestamp)} - $e\n\n")
                writer.flush()
            }

            while (true) {
                val (timestamp, event) = q.take()
                write(timestamp, event)
            }
        }
    }

    private
    fun bufferedWriter() =
        BufferedWriter(FileWriter(outputFile()))

    private
    fun outputFile() =
        createTempFile(prefix = "resolver-", suffix = ".log", directory = outputDir())

    private
    fun outputDir() =
        File(userHome(), logDirForOperatingSystem()).apply { mkdirs() }

    private
    fun logDirForOperatingSystem() =
        OperatingSystem.current().run {
            when {
                isMacOsX  -> "Library/Logs/gradle-script-kotlin"
                isWindows -> "Application Data/gradle-script-kotlin/log"
                else      -> ".gradle-script-kotlin/log"
            }
        }

    private
    fun now() = GregorianCalendar.getInstance().time
}

internal
sealed class ResolverEvent

private
data class ResolutionFailure(
    val scriptFile: File?,
    val failure: Exception) : ResolverEvent()

private
data class ResolutionProgress(
    val scriptFile: File?,
    val description: String) : ResolverEvent()

private
data class ResolvedToPrevious(
    val scriptFile: File?,
    val environment: Environment?,
    val previousDependencies: KotlinScriptExternalDependencies?) : ResolverEvent()

private
data class SubmittedModelRequest(
    val scriptFile: File?,
    val request: KotlinBuildScriptModelRequest) : ResolverEvent()

private
data class ReceivedModelResponse(
    val scriptFile: File?,
    val response: KotlinBuildScriptModel) : ResolverEvent()

private
data class ResolvedDependencies(
    val scriptFile: File?,
    val dependencies: KotlinBuildScriptDependencies) : ResolverEvent()
