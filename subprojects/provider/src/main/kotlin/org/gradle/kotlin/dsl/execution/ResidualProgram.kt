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


/**
 * The result of partially evaluating a Kotlin DSL [program][Program] of a certain [kind][ProgramKind]
 * against a given [target][ProgramTarget].
 *
 * @see PartialEvaluator.reduce
 */
internal
sealed class ResidualProgram {

    /**
     * A static residue, can be compiled ahead of time.
     */
    data class Static(val instructions: List<Instruction>) : ResidualProgram() {

        constructor(vararg instructions: Instruction)
            : this(instructions.toList())
    }

    /**
     * A dynamic script [source] residue, can only be compiled after the execution of the static [prelude] at runtime.
     */
    data class Dynamic(val prelude: Static, val source: ProgramSource) : ResidualProgram()

    sealed class Instruction {

        object CloseTargetScope : Instruction()

        object ApplyDefaultPluginRequests : Instruction()

        object ApplyBasePlugins : Instruction()

        data class ApplyPluginRequestsOf(val program: Program.Stage1) : Instruction()

        data class Eval(val script: ProgramSource) : Instruction()

        override fun toString(): String = javaClass.simpleName
    }
}
