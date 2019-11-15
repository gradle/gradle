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

import org.gradle.kotlin.dsl.execution.ResidualProgram.Dynamic
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyBasePlugins
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyDefaultPluginRequests
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyPluginRequestsOf
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.CloseTargetScope
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.Eval
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.SetupEmbeddedKotlin
import org.gradle.kotlin.dsl.execution.ResidualProgram.Static


enum class ProgramKind {
    TopLevel,
    ScriptPlugin
}


enum class ProgramTarget {
    Project,
    Settings,
    Gradle
}


/**
 * Reduces a [Program] into a [ResidualProgram] given its [kind][ProgramKind] and [target][ProgramTarget].
 */
internal
class PartialEvaluator(
    private val programKind: ProgramKind,
    private val programTarget: ProgramTarget
) {

    fun reduce(program: Program): ResidualProgram = when (program) {

        is Program.Empty -> reduceEmptyProgram()

        is Program.PluginManagement -> stage1WithPluginManagement(program)

        is Program.Buildscript -> reduceBuildscriptProgram(program)

        is Program.Plugins -> stage1WithPlugins(program)

        is Program.Stage1Sequence -> stage1WithPlugins(program)

        is Program.Script -> reduceScriptProgram(program)

        is Program.Staged -> reduceStagedProgram(program)

        else -> throw IllegalArgumentException("Unsupported `$program'")
    }

    private
    fun reduceEmptyProgram(): Static =

        when (programTarget) {

            ProgramTarget.Project ->

                when (programKind) {

                    ProgramKind.TopLevel -> Static(
                        SetupEmbeddedKotlin,
                        ApplyDefaultPluginRequests,
                        ApplyBasePlugins
                    )

                    ProgramKind.ScriptPlugin -> Static(
                        CloseTargetScope,
                        ApplyBasePlugins
                    )
                }

            else -> Static(defaultStageTransition())
        }

    private
    fun reduceBuildscriptProgram(program: Program.Buildscript): Static =
        Static(
            SetupEmbeddedKotlin,
            Eval(fragmentHolderSourceFor(program)),
            defaultStageTransition()
        )

    private
    fun fragmentHolderSourceFor(program: Program.FragmentHolder): ProgramSource {

        val fragment = program.fragment
        val section = fragment.section
        return fragment.source.map { sourceText ->
            sourceText
                .subText(0..section.block.last)
                .preserve(section.wholeRange)
        }
    }

    private
    fun reduceScriptProgram(program: Program.Script): ResidualProgram =

        when (programTarget) {

            ProgramTarget.Project -> {

                when (programKind) {

                    ProgramKind.TopLevel -> Dynamic(
                        Static(
                            SetupEmbeddedKotlin,
                            ApplyDefaultPluginRequests,
                            ApplyBasePlugins
                        ),
                        program.source
                    )

                    ProgramKind.ScriptPlugin -> Static(
                        CloseTargetScope,
                        ApplyBasePlugins,
                        Eval(program.source)
                    )
                }
            }

            else -> {
                Static(
                    defaultStageTransition(),
                    Eval(program.source)
                )
            }
        }

    private
    fun defaultStageTransition(): ResidualProgram.Instruction = when (programKind) {
        ProgramKind.TopLevel -> ApplyDefaultPluginRequests
        else -> CloseTargetScope
    }

    private
    fun reduceStagedProgram(program: Program.Staged): Dynamic =

        Dynamic(
            reduceStage1Program(program.stage1),
            program.stage2.source
        )

    private
    fun reduceStage1Program(stage1: Program.Stage1): Static = when (stage1) {

        is Program.Buildscript ->

            when (programTarget) {

                ProgramTarget.Project -> Static(
                    SetupEmbeddedKotlin,
                    Eval(fragmentHolderSourceFor(stage1)),
                    ApplyDefaultPluginRequests,
                    ApplyBasePlugins
                )

                else -> reduceBuildscriptProgram(stage1)
            }

        else -> stage1WithPlugins(stage1)
    }

    private
    fun stage1WithPlugins(stage1: Program.Stage1): Static =
        when (programTarget) {
            ProgramTarget.Project -> Static(
                SetupEmbeddedKotlin,
                ApplyPluginRequestsOf(stage1),
                ApplyBasePlugins
            )
            else -> Static(
                SetupEmbeddedKotlin,
                ApplyPluginRequestsOf(stage1)
            )
        }

    private
    fun stage1WithPluginManagement(program: Program.PluginManagement): Static {
        return Static(
            SetupEmbeddedKotlin,
            Eval(fragmentHolderSourceFor(program)),
            ApplyDefaultPluginRequests
        )
    }
}
