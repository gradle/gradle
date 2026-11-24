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

import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.kotlin.dsl.resolver.EditorMessages
import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity
import java.io.File


internal
fun buildEditorReportsFor(
    scriptFile: File?,
    exceptions: List<Exception>
): List<EditorReport> =
    if (scriptFile == null || exceptions.isEmpty()) emptyList()
    else inferEditorReportsFrom(scriptFile.canonicalFile, exceptions.asSequence())


private
fun inferEditorReportsFrom(
    scriptFile: File,
    exceptions: Sequence<Exception>
): List<EditorReport> {

    val locatedExceptions =
        exceptions.findLocationAwareExceptions()

    val reports =
        mutableListOf<EditorReport>()

    reportExceptionsNotLocatedIn(scriptFile, locatedExceptions, reports)

    reportRuntimeExceptionsLocatedIn(scriptFile, locatedExceptions, reports)

    return reports
}


private
fun reportExceptionsNotLocatedIn(
    scriptFile: File,
    exceptions: Sequence<LocationAwareException>,
    reports: MutableList<EditorReport>
) {
    if (exceptions.anyNotLocatedIn(scriptFile.path)) {
        reports.add(wholeFileWarning(EditorMessages.buildConfigurationFailed))
    }
}


private
fun reportRuntimeExceptionsLocatedIn(
    scriptFile: File,
    exceptions: Sequence<LocationAwareException>,
    reports: MutableList<EditorReport>
) {
    exceptions.runtimeFailuresLocatedInAndNotCausedScriptCompilation(scriptFile.path).forEach {
        reports.add(wholeFileWarning(EditorMessages.buildConfigurationFailedInCurrentScript))
    }
}


private
fun Sequence<Exception>.findLocationAwareExceptions(): Sequence<LocationAwareException> =
    mapNotNull(::firstLocationAwareCauseOrNull)


private
tailrec fun firstLocationAwareCauseOrNull(ex: Throwable): LocationAwareException? {
    if (ex is LocationAwareException) return ex
    val cause = ex.cause ?: return null
    return firstLocationAwareCauseOrNull(cause)
}


private
fun Sequence<LocationAwareException>.anyNotLocatedIn(scriptPath: String): Boolean =
    any { it.message?.contains(scriptPath) != true }


private
fun Sequence<Exception>.runtimeFailuresLocatedInAndNotCausedScriptCompilation(scriptPath: String): Sequence<LocationAwareException> =
    mapNotNull { it.runtimeFailureLocatedIn(scriptPath) }.filter { !it.isCausedByScriptCompilationException }


internal
fun Sequence<Exception>.runtimeFailuresLocatedIn(scriptPath: String): Sequence<LocationAwareException> =
    mapNotNull { it.runtimeFailureLocatedIn(scriptPath) }


private
tailrec fun Throwable.runtimeFailureLocatedIn(scriptPath: String): LocationAwareException? {
    if (this is LocationAwareException && message?.contains(scriptPath) == true) {
        return this
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
