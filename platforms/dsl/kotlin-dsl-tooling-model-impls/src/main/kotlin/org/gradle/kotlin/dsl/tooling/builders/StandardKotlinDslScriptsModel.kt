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

import org.gradle.tooling.model.kotlin.dsl.EditorPosition
import org.gradle.tooling.model.kotlin.dsl.EditorReport
import org.gradle.tooling.model.kotlin.dsl.EditorReportSeverity
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptModel
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel
import java.io.File
import java.io.Serializable
import kotlin.collections.plus


data class StandardKotlinDslScriptsModel(
    private val commonModel: CommonKotlinDslScriptModel,
    private val dehydratedScriptModels: Map<File, KotlinDslScriptModel>
) : KotlinDslScriptsModel, Serializable {

    override fun getScriptModels() =
        dehydratedScriptModels.mapValues { (_, lightModel) ->
            StandardKotlinDslScriptModel(
                commonModel.classPath + lightModel.classPath,
                commonModel.sourcePath + lightModel.sourcePath,
                commonModel.implicitImports + lightModel.implicitImports,
                lightModel.editorReports,
                lightModel.exceptions
            )
        }

}


data class CommonKotlinDslScriptModel(
    val classPath: List<File>,
    val sourcePath: List<File>,
    val implicitImports: List<String>
) : Serializable


data class StandardKotlinDslScriptModel(
    private val classPath: List<File>,
    private val sourcePath: List<File>,
    private val implicitImports: List<String>,
    private val editorReports: List<EditorReport>,
    private val exceptions: List<String>
) : KotlinDslScriptModel, Serializable {

    override fun getClassPath() = classPath

    override fun getSourcePath() = sourcePath

    override fun getImplicitImports() = implicitImports

    override fun getEditorReports() = editorReports

    override fun getExceptions() = exceptions
}


data class StandardEditorReport(
    private val severity: EditorReportSeverity,
    private val message: String,
    private val position: EditorPosition? = null
) : EditorReport, Serializable {

    override fun getSeverity() = severity

    override fun getMessage() = message

    override fun getPosition() = position
}


data class StandardEditorPosition(
    private val line: Int,
    private val column: Int = 0
) : EditorPosition, Serializable {

    override fun getLine() = line

    override fun getColumn() = column
}
