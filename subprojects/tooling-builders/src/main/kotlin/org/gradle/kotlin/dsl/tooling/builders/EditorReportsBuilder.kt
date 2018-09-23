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

package org.gradle.kotlin.dsl.tooling.builders

import org.gradle.kotlin.dsl.tooling.models.EditorMessages
import org.gradle.kotlin.dsl.tooling.models.EditorPosition
import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity

import org.gradle.internal.exceptions.LocationAwareException

import java.io.File
import java.io.Serializable


internal
fun buildEditorReportsFor(scriptFile: File?, exceptions: List<Exception>): List<EditorReport> =
    if (scriptFile == null || exceptions.isEmpty()) emptyList()
    else inferEditorReportsFrom(scriptFile.canonicalFile, exceptions.asSequence())


private
fun inferEditorReportsFrom(scriptFile: File, exceptions: Sequence<Exception>): List<EditorReport> {
    val reports = mutableListOf<EditorReport>()
    if (exceptions.containsExceptionChainUnrelatedTo(scriptFile.path)) {
        reports.add(wholeFileWarning(EditorMessages.buildConfigurationFailed))
    }
    val actualLinesRange = scriptFile.readLinesRange()
    exceptions.runtimeFailuresLocatedIn(scriptFile.path).forEach { failure ->
        if (failure.lineNumber in actualLinesRange) {
            reports.add(lineError(failure.cause!!.message!!, failure.lineNumber))
        } else {
            reports.add(wholeFileError(EditorMessages.buildConfigurationFailedInCurrentScript))
        }
    }
    return reports
}


private
fun File.readLinesRange() =
    1..readLines().size


private
fun Sequence<Exception>.containsExceptionChainUnrelatedTo(scriptPath: String): Boolean =
    map { it.joinedCausesMessagesOf() }.any { !it.contains(scriptPath) }


private
tailrec fun Throwable.joinedCausesMessagesOf(acc: String = ""): String {
    var joined = acc
    if (message != null) joined += "\n$message"
    val next = cause ?: return joined
    return next.joinedCausesMessagesOf(joined)
}


private
fun Sequence<Exception>.runtimeFailuresLocatedIn(scriptPath: String): Sequence<LocationAwareException> =
    mapNotNull { it.runtimeFailureLocatedIn(scriptPath) }


private
tailrec fun Throwable.runtimeFailureLocatedIn(scriptPath: String): LocationAwareException? {
    if (this is LocationAwareException && message?.contains(scriptPath) == true) {
        return if (isCausedByScriptCompilationException) null
        else this
    }
    val next = cause ?: return null
    return next.runtimeFailureLocatedIn(scriptPath)
}


/**
 * Check if this [LocationAwareException] is caused by a Gradle Kotlin DSL `ScriptCompilationException`.
 *
 * Compares class names because `ScriptCompilationException` from :provider isn't available here.
 */
private
val LocationAwareException.isCausedByScriptCompilationException
    get() = cause?.let { it::class.java.name == "org.gradle.kotlin.dsl.support.ScriptCompilationException" } == true


private
fun wholeFileWarning(message: String) =
    DefaultEditorReport(EditorReportSeverity.WARNING, message)


private
fun wholeFileError(message: String) =
    DefaultEditorReport(EditorReportSeverity.ERROR, message)


private
fun lineError(message: String, line: Int) =
    DefaultEditorReport(EditorReportSeverity.ERROR, message, DefaultEditorPosition(line))


private
data class DefaultEditorReport(
    override val severity: EditorReportSeverity,
    override val message: String,
    override val position: EditorPosition? = null
) : EditorReport, Serializable


private
data class DefaultEditorPosition(
    override val line: Int,
    override val column: Int = 0
) : EditorPosition, Serializable
