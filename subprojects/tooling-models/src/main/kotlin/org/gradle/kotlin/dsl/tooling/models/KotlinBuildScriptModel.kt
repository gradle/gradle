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
package org.gradle.kotlin.dsl.tooling.models

import java.io.File


interface KotlinBuildScriptModel {

    val classPath: List<File>
    val sourcePath: List<File>
    val implicitImports: List<String>
    val editorReports: List<EditorReport>
    val exceptions: List<Exception>
}


interface EditorReport {

    val severity: EditorReportSeverity
    val message: String
    val position: EditorPosition?
}


enum class EditorReportSeverity {
    WARNING
}


interface EditorPosition {

    val line: Int
    val column: Int
}


object EditorMessages {
    const val failure = "Script dependencies resolution failed"
    const val failureUsingPrevious = "Script dependencies resolution failed, using previous dependencies"

    const val buildConfigurationFailed = "Build configuration failed, run 'gradle tasks' for more information"
}
