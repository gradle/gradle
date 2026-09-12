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

package org.gradle.kotlin.dsl.support

import org.gradle.internal.logging.ConsoleRenderer
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.Severity
import org.jetbrains.kotlin.buildtools.api.CompilerMessageRenderer.SourceLocation
import org.slf4j.Logger
import java.io.File


internal
fun messageCollectorFor(
    log: Logger,
    allWarningsAsErrors: Boolean,
    pathTranslation: (String) -> String,
): LoggingMessageRenderer =
    messageCollectorFor(log, onCompilerWarningsFor(allWarningsAsErrors), pathTranslation)


internal
fun messageCollectorFor(
    log: Logger,
    onCompilerWarning: CompilerWarning = CompilerWarning.WARN,
    pathTranslation: (String) -> String = { it }
): LoggingMessageRenderer =
    LoggingMessageRenderer(log, onCompilerWarning, pathTranslation)


internal
data class ScriptCompilationError(val message: String, val location: SourceLocation?)


internal
data class ScriptCompilationException(private val scriptCompilationErrors: List<ScriptCompilationError>) : RuntimeException() {

    val errors: List<ScriptCompilationError> by unsafeLazy {
        scriptCompilationErrors.filter { it.location == null } +
                scriptCompilationErrors.filter { it.location != null }
                    .sortedBy { it.location!!.line }
    }

    init {
        require(scriptCompilationErrors.isNotEmpty())
    }

    val firstErrorLine
        get() = errors.firstNotNullOfOrNull { it.location?.line }

    override val message: String
        get() = (
                listOf("Script compilation $errorPlural:")
                        + indentedErrorMessages()
                        + "${errors.size} $errorPlural"
                )
            .joinToString("\n\n")

    private
    fun indentedErrorMessages() =
        errors.map { prependIndent(errorMessage(it)) }

    private
    fun errorMessage(error: ScriptCompilationError): String =
        error.location?.let { location ->
            errorAt(location, error.message)
        } ?: error.message

    private
    fun errorAt(location: SourceLocation, message: String): String {
        val columnIndent = " ".repeat(5 + maxLineNumberStringLength + 1 + location.column)
        return "Line ${lineNumber(location)}: ${location.lineContent}\n" +
                "^ $message".lines().joinToString(
                    prefix = columnIndent,
                    separator = "\n$columnIndent  $INDENT"
                )
    }

    private
    fun lineNumber(location: SourceLocation) =
        location.line.toString().padStart(maxLineNumberStringLength, '0')

    private
    fun prependIndent(it: String) = it.prependIndent(INDENT)

    private
    val errorPlural
        get() = if (errors.size > 1) "errors" else "error"

    private
    val maxLineNumberStringLength: Int by lazy {
        errors.mapNotNull { it.location?.line }.maxOrNull()?.toString()?.length ?: 0
    }
}


private
const val INDENT = "  "


internal
enum class CompilerWarning {
    FAIL, WARN, DEBUG
}


private
fun onCompilerWarningsFor(allWarningsAsErrors: Boolean) =
    if (allWarningsAsErrors) CompilerWarning.FAIL
    else CompilerWarning.WARN


internal
class LoggingMessageRenderer(
    val log: Logger,
    private val onCompilerWarning: CompilerWarning,
    private val pathTranslation: (String) -> String,
) : CompilerMessageRenderer {
    val errors = arrayListOf<ScriptCompilationError>()

    override fun render(severity: Severity, message: String, location: SourceLocation?): String {
        fun msg() =
            location?.run {
                path.let(pathTranslation).let { path ->
                    when {
                        line >= 0 && column >= 0 -> compilerMessageFor(path, line, column, message)
                        else -> "${clickableFileUrlFor(path)}: $message"
                    }
                }
            } ?: message

        fun taggedMsg() =
            "${severity.name[0].lowercase()}: ${msg()}"

        fun onError(): String {
            errors += ScriptCompilationError(message, location)
            return taggedMsg().also { log.error { it } }
        }

        fun onWarning(): String {
            return when (onCompilerWarning) {
                CompilerWarning.FAIL -> onError()
                CompilerWarning.WARN -> taggedMsg().also { log.warn { it } }
                CompilerWarning.DEBUG -> taggedMsg().also { log.debug { it } }
            }
        }

        return when (severity) {
            Severity.ERROR -> onError()
            Severity.WARNING -> onWarning()
            Severity.INFO -> msg().also { log.info { it } }
            Severity.DEBUG -> taggedMsg().also { log.debug { it } }
        }
    }

}


internal
fun compilerMessageFor(path: String, line: Int, column: Int, message: String) =
    "${clickableFileUrlFor(path)}:$line:$column: $message"


private
fun clickableFileUrlFor(path: String): String =
    ConsoleRenderer().asClickableFileUrl(File(path))
