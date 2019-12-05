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

package org.gradle.kotlin.dsl.execution

import org.gradle.api.Project

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.support.KotlinScriptHost
import org.gradle.kotlin.dsl.support.useToRun

import org.gradle.plugin.management.internal.PluginRequests


abstract class ExecutableProgram {

    abstract fun execute(programHost: Host, scriptHost: KotlinScriptHost<*>)

    interface Host {

        fun setupEmbeddedKotlinFor(
            scriptHost: KotlinScriptHost<*>
        )

        /**
         * Invoked by a [top-level][ProgramKind.TopLevel] [Project][ProgramTarget.Project] program
         * after stage 1 completes. All other program types invoke [closeTargetScopeOf] to signal the completion
         * of stage 1.
         */
        fun applyPluginsTo(
            scriptHost: KotlinScriptHost<*>,
            pluginRequests: PluginRequests
        )

        /**
         * Invoked by a [Project][ProgramTarget.Project] program immediately after stage 1 completes.
         */
        fun applyBasePluginsTo(
            project: Project
        )

        fun closeTargetScopeOf(
            scriptHost: KotlinScriptHost<*>
        )

        fun accessorsClassPathFor(
            scriptHost: KotlinScriptHost<*>
        ): ClassPath

        fun evaluateSecondStageOf(
            program: StagedProgram,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode,
            accessorsClassPath: ClassPath?
        )

        fun compileSecondStageOf(
            program: StagedProgram,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode,
            programKind: ProgramKind,
            programTarget: ProgramTarget,
            accessorsClassPath: ClassPath?
        ): CompiledScript

        fun handleScriptException(
            exception: Throwable,
            scriptClass: Class<*>,
            scriptHost: KotlinScriptHost<*>
        )
    }

    abstract class StagedProgram : ExecutableProgram() {

        abstract val secondStageScriptText: String

        abstract fun loadSecondStageFor(
            programHost: Host,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode,
            accessorsClassPath: ClassPath?
        ): CompiledScript

        fun loadScriptResource(resourcePath: String): String =
            javaClass.getResourceAsStream(resourcePath).bufferedReader().useToRun {
                readText()
            }
    }
}
