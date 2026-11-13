/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.kotlin.dsl.tooling.models.EditorPosition
import org.gradle.kotlin.dsl.tooling.models.EditorReport
import org.gradle.kotlin.dsl.tooling.models.EditorReportSeverity
import org.gradle.kotlin.dsl.tooling.models.KotlinBuildScriptModel
import java.io.File
import java.io.Serializable


data class StandardKotlinBuildScriptModel(
    private val classPath: List<File>,
    private val sourcePath: List<File>,
    private val implicitImports: List<String>,
    private val editorReports: List<EditorReport>,
    private val exceptions: List<String>,
    private val enclosingScriptProjectDir: File?
) : KotlinBuildScriptModel, Serializable {

    override fun getClassPath() = classPath

    override fun getSourcePath() = sourcePath

    override fun getImplicitImports() = implicitImports

    override fun getEditorReports() = editorReports

    override fun getExceptions() = exceptions

    override fun getEnclosingScriptProjectDir() = enclosingScriptProjectDir
}


data class DefaultEditorReport(
    private val severity: EditorReportSeverity,
    private val message: String,
    private val position: EditorPosition? = null
) : EditorReport, Serializable {

    override fun getSeverity() = severity

    override fun getMessage() = message

    override fun getPosition() = position
}


data class DefaultEditorPosition(
    private val line: Int,
    private val column: Int = 0
) : EditorPosition, Serializable {

    override fun getLine() = line

    override fun getColumn() = column
}
