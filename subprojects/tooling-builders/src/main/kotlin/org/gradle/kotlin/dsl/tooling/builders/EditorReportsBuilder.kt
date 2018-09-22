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

import java.io.File
import java.io.Serializable


internal
fun buildEditorReportsFor(scriptFile: File?, exceptions: List<Exception>): List<EditorReport> =
    if (exceptions.isEmpty()) emptyList()
    else listOf(
        wholeFileWarning(EditorMessages.buildConfigurationFailed)
    )


private
fun wholeFileWarning(message: String) =
    DefaultEditorReport(EditorReportSeverity.WARNING, message)


private
data class DefaultEditorReport(
    override val severity: EditorReportSeverity,
    override val message: String,
    override val position: EditorPosition? = null
) : EditorReport, Serializable
