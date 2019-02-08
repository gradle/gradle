/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.internal.os.OperatingSystem

import org.gradle.kotlin.dsl.concurrent.EventLoop
import org.gradle.kotlin.dsl.support.userHome

import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel

import com.google.common.annotations.VisibleForTesting

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter

import java.text.SimpleDateFormat

import java.util.*

import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties


@VisibleForTesting
interface ResolverEventLogger {
    fun log(event: ResolverEvent)
}


internal
object DefaultResolverEventLogger : ResolverEventLogger {

    override fun log(event: ResolverEvent) {
        eventLoop.accept(now() to event)
    }

    private
    val eventLoop = EventLoop<Pair<Date, ResolverEvent>> { poll ->

        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

        bufferedAppendWriter().use { writer ->

            fun write(timestamp: Date, e: ResolverEvent) {
                try {
                    writer.write("${format.format(timestamp)} - ${prettyPrint(e)}\n\n")
                } catch (e: Exception) {
                    e.printStackTrace(PrintWriter(writer))
                } finally {
                    writer.flush()
                }
            }

            while (true) {
                val (timestamp, event) = poll() ?: break
                write(timestamp, event)
            }
        }
    }

    private
    val outputFile by lazy {
        outputDir().let { logDir ->
            cleanupLogDirectory(logDir)
            logDir.resolve("resolver-${timestampForFileName()}.log")
        }
    }

    private
    fun bufferedAppendWriter() =
        BufferedWriter(FileWriter(outputFile, true))

    private
    fun timestampForFileName() =
        SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS").format(now())

    private
    fun outputDir() =
        logDirForOperatingSystem().apply { mkdirs() }

    private
    fun logDirForOperatingSystem() =
        OperatingSystem.current().run {
            when {
                isMacOsX -> userHome().resolve("Library/Logs/gradle-kotlin-dsl")
                isWindows -> System.getenv("LOCALAPPDATA")
                    ?.let { File("$it/gradle-kotlin-dsl/log") }
                    ?: userHome().resolve("AppData/Local/gradle-kotlin-dsl/log")
                else -> userHome().resolve(".gradle-kotlin-dsl/log")
            }
        }

    private
    fun now() = GregorianCalendar.getInstance().time

    private
    const val cleanupAfterDays = 1

    private
    const val logFilesExpireAfterDays = 7

    private
    fun cleanupLogDirectory(logDir: File) =
        readyForCleanup(logDir) {
            val expiration = daysAgo(logFilesExpireAfterDays)
            logDir.listFiles { file ->
                file.isFile && file.name.matches(resolverLogFilenameRegex) && Date(file.lastModified()).before(expiration)
            }.forEach { it.delete() }
        }

    private
    fun readyForCleanup(logDir: File, cleanup: () -> Unit) =
        logDir.resolve(".cleanup").run {
            if (!isFile || (isFile && Date(lastModified()).before(daysAgo(cleanupAfterDays)))) {
                cleanup()
                writeBytes(ByteArray(0))
            }
        }

    private
    fun daysAgo(days: Int) =
        GregorianCalendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -days) }.time

    private
    val resolverLogFilenameRegex =
        Regex("resolver-\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{3}\\.log")
}


@VisibleForTesting
fun prettyPrint(e: ResolverEvent): String = e.run {
    when (this) {
        is SubmittedModelRequest ->
            prettyPrint(
                "SubmittedModelRequest",
                sequenceOf(
                    "correlationId" to correlationId,
                    "scriptFile" to scriptFile,
                    "request" to prettyPrintAny(request, indentation = 2)))

        is ReceivedModelResponse ->
            prettyPrint(
                "ReceivedModelResponse",
                sequenceOf(
                    "correlationId" to correlationId,
                    "scriptFile" to scriptFile,
                    "response" to prettyPrint(response, indentation = 2)))

        is ResolutionFailure ->
            prettyPrint(
                "ResolutionFailure",
                sequenceOf(
                    "correlationId" to correlationId,
                    "scriptFile" to scriptFile,
                    "failure" to stringForException(failure, indentation = 2)))
        else ->
            prettyPrintAny(this)
    }
}


private
fun prettyPrintAny(any: Any, indentation: Int? = null) =
    prettyPrint(
        any::class.simpleName,
        any::class
            .declaredMemberProperties
            .asSequence()
            .filterIsInstance<KProperty1<Any, Any?>>()
            .map { it.name to it.get(any) },
        indentation)


private
fun prettyPrint(model: KotlinBuildScriptModel, indentation: Int?) = model.run {
    prettyPrint(
        "KotlinBuildScriptModel",
        sequenceOf(
            "classPath" to compactStringFor(classPath),
            "sourcePath" to compactStringFor(sourcePath),
            "implicitImports" to compactStringFor(implicitImports, '.'),
            "editorReports" to editorReports.toString(),
            "exceptions" to stringForExceptions(exceptions, indentation)),
        indentation)
}


private
fun prettyPrint(className: String?, properties: Sequence<Pair<String, Any?>>, indentation: Int? = null) =
    "$className(${prettyPrint(properties, indentation)})"


private
fun prettyPrint(properties: Sequence<Pair<String, Any?>>, indentation: Int?) =
    indentationStringFor(indentation).let {
        properties.joinToString(prefix = "\n$it", separator = ",\n$it") { (name, value) ->
            "$name = $value"
        }
    }


private
fun stringForExceptions(exceptions: List<String>, indentation: Int?) =
    if (exceptions.isNotEmpty())
        indentationStringFor(indentation).let {
            exceptions.joinToString(prefix = "[\n$it\t", separator = ",\n$it\t", postfix = "]") { exception ->
                stringForException(exception, indentation)
            }
        }
    else "NO ERROR"


private
fun stringForException(exception: Exception, indentation: Int?) =
    stringForException(
        StringWriter().also { exception.printStackTrace(PrintWriter(it)) }.toString(),
        indentation
    )


private
fun stringForException(exception: String, indentation: Int?) =
    indentationStringFor(indentation).let {
        exception.prependIndent(it)
    }


private
fun indentationStringFor(indentation: Int?) =
    when (indentation) {
        null, 1 -> "\t"
        else -> "\t\t"
    }
