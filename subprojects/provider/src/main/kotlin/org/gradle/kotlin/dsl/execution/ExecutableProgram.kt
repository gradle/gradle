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

import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.support.KotlinScriptHost


abstract class ExecutableProgram {

    abstract fun execute(programHost: Host, scriptHost: KotlinScriptHost<*>)

    interface Host {

        fun closeTargetScopeOf(
            scriptHost: KotlinScriptHost<*>
        )

        fun evaluateSecondStageOf(
            program: StagedProgram,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode
        )

        fun compileSecondStageScript(
            scriptPath: String,
            originalScriptPath: String,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode,
            programKind: ProgramKind,
            programTarget: ProgramTarget
        ): Class<*>

        fun handleScriptException(
            exception: Throwable,
            scriptClass: Class<*>,
            scriptHost: KotlinScriptHost<*>
        )
    }

    open class Empty : ExecutableProgram() {

        override fun execute(programHost: Host, scriptHost: KotlinScriptHost<*>) {
            programHost.closeTargetScopeOf(scriptHost)
        }
    }

    abstract class StagedProgram : ExecutableProgram() {

        abstract fun loadSecondStageFor(
            programHost: Host,
            scriptHost: KotlinScriptHost<*>,
            scriptTemplateId: String,
            sourceHash: HashCode
        ): Class<*>
    }

    object Runtime {

        /**
         * Invoked by an specialized program to signal an exception happened during script execution.
         *
         * This function is here mainly to keep the emitted bytecode (and corresponding [ResidualProgramCompiler] code) simple.
         */
        @JvmStatic
        fun onScriptException(
            exception: Throwable,
            scriptClass: Class<*>,
            scriptHost: KotlinScriptHost<*>,
            programHost: Host
        ) {
            programHost.handleScriptException(exception, scriptClass, scriptHost)
        }
    }
}
