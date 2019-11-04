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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class PartialEvaluatorTest {

    @Test
    fun `an empty top-level Project script`() {

        assertThat(
            "reduces to static program that applies default plugin requests and base plugins",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyDefaultPluginRequests,
                    ApplyBasePlugins
                )))
    }

    @Test
    fun `a non-empty top-level Project script`() {

        val source = ProgramSource("build.gradle.kts", "dynamic")
        assertThat(
            "reduces to dynamic program that applies default plugin requests and base plugins",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyDefaultPluginRequests,
                        ApplyBasePlugins
                    ),
                    source
                )))
    }

    @Test
    fun `a non-empty top-level Project script with a buildscript block`() {

        val buildscriptFragment =
            fragment("buildscript", "...")

        val source =
            ProgramSource("build.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that evaluates buildscript block, applies default plugin requests and base plugins",
            partialEvaluationOf(
                Program.Staged(
                    Program.Buildscript(buildscriptFragment),
                    Program.Script(source)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        Eval(buildscriptFragment.source),
                        ApplyDefaultPluginRequests,
                        ApplyBasePlugins
                    ),
                    source
                )))
    }

    @Test
    fun `a top-level Project plugins block`() {

        val program =
            Program.Plugins(fragment("plugins", "..."))

        assertThat(
            "reduces to static program that applies plugin requests and base plugins",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyPluginRequestsOf(program),
                    ApplyBasePlugins
                )))
    }

    @Test
    fun `a top-level Settings plugins block`() {

        val program =
            Program.Plugins(fragment("plugins", "..."))

        assertThat(
            "reduces to static program that applies plugin requests without the base plugin",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyPluginRequestsOf(program)
                )))
    }

    @Test
    fun `a top-level Settings pluginManagement block`() {

        val fragment = fragment("pluginManagement", "...")
        val program = Program.PluginManagement(fragment)

        assertThat(
            "reduces to static program that evaluates pluginManagement block then applies default plugin requests",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    ApplyDefaultPluginRequests
                )))
    }

    @Test
    fun `a top-level Project buildscript block followed by plugins block`() {

        val program =
            Program.Stage1Sequence(
                null,
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", "...")))

        assertThat(
            "reduces to static program that applies plugin requests and base plugins",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyPluginRequestsOf(program),
                    ApplyBasePlugins
                )))
    }

    @Test
    fun `a top-level Settings buildscript block followed by plugins block`() {

        val program =
            Program.Stage1Sequence(
                null,
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", "...")))

        assertThat(
            "reduces to static program that applies plugin requests without base plugins",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyPluginRequestsOf(program)
                )))
    }

    @Test
    fun `a non-empty top-level Project script with a buildscript block followed by plugins block`() {

        val program =
            Program.Staged(
                Program.Stage1Sequence(
                    null,
                    Program.Buildscript(fragment("buildscript", "...")),
                    Program.Plugins(fragment("plugins", "..."))
                ),
                Program.Script(ProgramSource("script.gradle.kts", "..."))
            )

        assertThat(
            "reduces to static program that applies plugin requests and base plugins",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                        ApplyBasePlugins
                    ),
                    program.stage2.source
                )))
    }

    @Test
    fun `empty Project script plugin`() {

        assertThat(
            "reduces to static program that closes target scope then applies base plugins",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.ScriptPlugin,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope,
                    ApplyBasePlugins
                )))
    }

    @Test
    fun `a non-empty Project script plugin`() {

        val source =
            ProgramSource("script-plugin.gradle.kts", "a script plugin")

        assertThat(
            "reduces to static program that closes target scope, applies base plugins then evaluates precompiled script",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope,
                    ApplyBasePlugins,
                    Eval(source)
                )))
    }

    @Test
    fun `an empty Settings script plugin`() {

        assertThat(
            "reduces to static program that closes target scope",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(CloseTargetScope)
            ))
    }

    @Test
    fun `a non-empty Settings script plugin`() {

        val source =
            ProgramSource("script-plugin.gradle.kts", "a script plugin")

        assertThat(
            "reduces to static program that closes target scope then evaluates precompiled script",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope,
                    Eval(source)
                )))
    }

    @Test
    fun `an empty Settings top-level script`() {

        assertThat(
            "reduces to static program that applies default plugin requests",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    ApplyDefaultPluginRequests
                )))
    }

    @Test
    fun `a non-empty Settings top-level script`() {

        val source =
            ProgramSource("settings.gradle.kts", "include(\"foo\", \"bar\")")

        assertThat(
            "reduces to static program that closes target scope",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    ApplyDefaultPluginRequests,
                    Eval(source)
                )))
    }

    @Test
    fun `a top-level Settings buildscript block`() {

        val fragment =
            fragment("buildscript", "...")

        assertThat(
            "reduces to static program that evalutes precompiled script then closes target scope",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    ApplyDefaultPluginRequests
                )))
    }

    @Test
    fun `a top-level Settings script with a buildscript block`() {

        val originalSource =
            ProgramSource(
                "settings.gradle.kts",
                "\nbuildscript { dependencies {} }; include(\"stage-2\")")

        val buildscriptFragment =
            originalSource.fragment(1..10, 12..31)

        val scriptSource =
            originalSource.map { text("\n                               ; include(\"stage-2\")") }

        val expectedEvalSource =
            originalSource.map { text("\nbuildscript { dependencies {} }") }

        assertThat(
            "reduces to dynamic program that evaluates precompiled script then closes target scope in its prelude",
            partialEvaluationOf(
                Program.Staged(
                    Program.Buildscript(buildscriptFragment),
                    Program.Script(scriptSource)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        Eval(expectedEvalSource),
                        ApplyDefaultPluginRequests
                    ),
                    scriptSource
                )))
    }

    private
    fun partialEvaluationOf(
        program: Program,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ): ResidualProgram = PartialEvaluator(programKind, programTarget).reduce(program)

    private
    fun isResidualProgram(program: ResidualProgram) =
        equalTo<ResidualProgram>(program)
}
