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

import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.kotlin.dsl.execution.ProgramKind
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.execution.templateIdFor
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import java.io.File


internal
interface KotlinDslCacheFixture {
    fun misses(vararg cachedScripts: CachedScript)
    fun hits(vararg cachedScripts: CachedScript)
}


sealed class CachedScript {

    class WholeFile(
        val stage1: CompilationStage,
        val stage2: CompilationStage
    ) : CachedScript() {

        val stages = listOf(stage1, stage2)
    }

    class CompilationStage(
        programTarget: ProgramTarget,
        programKind: ProgramKind,
        val stage: String,
        sourceDescription: String,
        val file: File,
        val enabled: Boolean = true
    ) : CachedScript() {

        val source = "$sourceDescription '$file'"
        val templateId = templateIdFor(programTarget, programKind, stage)
    }
}


internal
fun ExecutionResult.assertOccurrenceCountOf(actionDisplayName: String, stage: CachedScript.CompilationStage, count: Int) {
    val expectedCount = if (stage.enabled) count else 0
    val logStatement = "${actionDisplayName.uppercaseFirstChar()} ${stage.templateId} from ${stage.source}"
    val observedCount = output.occurrenceCountOf(logStatement)
    require(observedCount == expectedCount) {
        "Expected $expectedCount but got $observedCount\n" +
            "  Looking for statement: $logStatement\n" +
            "  Build output was:\n" + output.prependIndent("    ")
    }
}


private
fun String.occurrenceCountOf(string: String) =
    split(string).size - 1
