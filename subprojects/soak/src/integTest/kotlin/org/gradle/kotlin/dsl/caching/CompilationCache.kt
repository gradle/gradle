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

package org.gradle.kotlin.dsl.caching

import org.gradle.integtests.fixtures.executer.ExecutionResult

import org.gradle.kotlin.dsl.execution.ProgramKind
import org.gradle.kotlin.dsl.execution.ProgramKind.ScriptPlugin
import org.gradle.kotlin.dsl.execution.ProgramKind.TopLevel
import org.gradle.kotlin.dsl.execution.ProgramTarget
import org.gradle.kotlin.dsl.execution.ProgramTarget.Gradle
import org.gradle.kotlin.dsl.execution.ProgramTarget.Project
import org.gradle.kotlin.dsl.execution.ProgramTarget.Settings
import org.gradle.kotlin.dsl.execution.templateIdFor

import java.io.File


fun ExecutionResult.compilationCache(action: CompilationCache.() -> Unit) =
    action(CompilationCache(this))


class CompilationCache(val result: ExecutionResult) {

    fun misses(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertCompilations(it, 1) }

    fun hits(vararg cachedScripts: CachedScript) =
        cachedScripts.forEach { assertCompilations(it, 0) }

    fun assertCompilations(cachedScript: CachedScript, count: Int) =
        when (cachedScript) {
            is CachedScript.WholeFile -> cachedScript.stages.forEach { assertCompilations(it, count) }
            is CachedScript.CompilationStage -> assertCompilations(cachedScript, count)
        }

    fun assertCompilations(stage: CachedScript.CompilationStage, count: Int) =
        result.assertOccurrenceCountOf("compiling", stage, count)
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


fun cachedInitializationFile(file: File, hasInitscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Gradle, TopLevel, "stage1", Descriptions.initializationScript, file),
        stage2 = CachedScript.CompilationStage(Gradle, TopLevel, "stage2", Descriptions.initializationScript, file, hasInitscriptBlock && hasBody)
    )


fun cachedGradleScript(file: File, hasInitscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Gradle, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Gradle, ScriptPlugin, "stage2", Descriptions.script, file, hasInitscriptBlock && hasBody)
    )


fun cachedSettingsFile(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Settings, TopLevel, "stage1", Descriptions.settingsFile, file),
        stage2 = CachedScript.CompilationStage(Settings, TopLevel, "stage2", Descriptions.settingsFile, file, hasBuildscriptBlock && hasBody)
    )


fun cachedSettingsScript(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Settings, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Settings, ScriptPlugin, "stage2", Descriptions.script, file, hasBuildscriptBlock && hasBody)
    )


fun cachedBuildFile(file: File, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Project, TopLevel, "stage1", Descriptions.buildFile, file),
        stage2 = CachedScript.CompilationStage(Project, TopLevel, "stage2", Descriptions.buildFile, file, hasBody)
    )


fun cachedProjectScript(file: File, hasBuildscriptBlock: Boolean = false, hasBody: Boolean = false) =
    CachedScript.WholeFile(
        stage1 = CachedScript.CompilationStage(Project, ScriptPlugin, "stage1", Descriptions.script, file),
        stage2 = CachedScript.CompilationStage(Project, ScriptPlugin, "stage2", Descriptions.script, file, hasBuildscriptBlock && hasBody)
    )


private
object Descriptions {
    const val initializationScript = "initialization script"
    const val settingsFile = "settings file"
    const val buildFile = "build file"
    const val script = "script"
}
