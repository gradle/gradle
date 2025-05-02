/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.caching.fixtures

import java.io.File


internal
fun compilationTrace(projectRoot: File, action: CompileTrace.() -> Unit) {
    val file = File(projectRoot, "operation-trace-log.txt")
    action(CompileTrace(file.readLines()))
}


internal
class CompileTrace(private val operations: List<String>) {

    fun assertScriptCompile(stage: CachedScript.CompilationStage) {
        val description = operationDescription(stage)
        require(operations.any { it.contains(description) }) {
            "Expecting operation `$description`!"
        }
    }

    fun assertNoScriptCompile(stage: CachedScript.CompilationStage) {
        val description = operationDescription(stage)
        require(!operations.any { it.contains(description) }) {
            "Unexpected operation `$description`!"
        }
    }

    private
    fun operationDescription(stage: CachedScript.CompilationStage) =
        "Compile script ${stage.file.name} (${descriptionOf(stage)})"

    private
    fun descriptionOf(stage: CachedScript.CompilationStage) =
        if (stage.stage == "stage1") "CLASSPATH" else "BODY"
}
