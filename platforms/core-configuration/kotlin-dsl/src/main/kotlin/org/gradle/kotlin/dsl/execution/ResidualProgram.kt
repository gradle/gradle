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

        constructor(vararg instructions: Instruction) :
            this(instructions.toList())
    }

    /**
     * A dynamic script [source] residue, can only be compiled after the execution of the static [prelude] at runtime.
     */
    data class Dynamic(val prelude: Static, val source: ProgramSource) : ResidualProgram()

    sealed class Instruction {

        /**
         * Causes the configuration of the embedded Kotlin libraries
         * on the host's ScriptHandler.
         */
        object SetupEmbeddedKotlin : Instruction()

        /**
         * Causes the target scope to be closed without applying any plugins.
         */
        object CloseTargetScope : StageTransition, Instruction()

        /**
         * Causes the evaluation of the compiled [script] against the script host
         * with `buildscript {}` block context, collecting project script dependencies.
         */
        data class CollectProjectScriptDependencies(val script: ProgramSource) : Instruction()

        /**
         * Causes the target scope to be closed by applying a default set of plugin requests that includes
         * the set of [auto-applied plugins][org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler].
         */
        object ApplyDefaultPluginRequests : StageTransition, Instruction()

        /**
         * Causes the target scope to be closed by applying the plugin requests collected during the execution
         * of the given [program] plus the set of [auto-applied plugins][org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler].
         */
        data class ApplyPluginRequestsOf(val program: Program.Stage1) : StageTransition, Instruction()

        /**
         * Causes the target scope to be closed with the plugin [requests] declared in the given [source]
         * program plus the set of [auto-applied plugins][org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler].
         */
        data class ApplyPluginRequests(
            val requests: List<PluginRequestSpec>,
            val source: Program.Plugins? = null
        ) : StageTransition, Instruction()

        /**
         * An instruction that marks the transition from stage 1 to stage 2 by causing the
         * target scope to be closed thus making the resolved classpath available to stage 2.
         *
         * A valid [Static] program must contain one and only one [StageTransition] instruction.
         */
        interface StageTransition

        /**
         * Causes the Kotlin DSL base plugins to be applied.
         */
        object ApplyBasePlugins : Instruction()

        /**
         * Causes the evaluation of the compiled [script] against the script host.
         */
        data class Eval(val script: ProgramSource) : Instruction()

        override fun toString(): String = javaClass.simpleName
    }

    data class PluginRequestSpec(val id: String, val version: String? = null, val apply: Boolean = true)
}
