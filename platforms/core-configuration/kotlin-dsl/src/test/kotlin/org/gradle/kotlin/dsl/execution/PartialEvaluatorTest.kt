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
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyPluginRequests
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyPluginRequestsOf
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.CloseTargetScope
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.CollectProjectScriptDependencies
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.Eval
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.SetupEmbeddedKotlin
import org.gradle.kotlin.dsl.execution.ResidualProgram.PluginRequestSpec
import org.gradle.kotlin.dsl.execution.ResidualProgram.Static
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.fail
import org.junit.Test


class PartialEvaluatorTest {

    @Test
    fun `Project target - top-level - empty`() {

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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - non-empty body`() {

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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript block - empty body`() {

        val fragment = fragment("buildscript", "...")

        assertThat(
            "reduces to static program that collects project script dependencies",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    CollectProjectScriptDependencies(fragment.source),
                    ApplyDefaultPluginRequests
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript block - non-empty body`() {

        val buildscriptFragment =
            fragment("buildscript", "...")

        val source =
            ProgramSource("build.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that collects project script dependencies, applies default plugin requests then base plugins",
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
                        CollectProjectScriptDependencies(buildscriptFragment.source),
                        ApplyDefaultPluginRequests,
                        ApplyBasePlugins
                    ),
                    source
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - plugins block - empty body`() {

        val program =
            Program.Plugins(fragment("plugins", "..."))

        assertThat(
            "reduces to static program that applies plugin requests then base plugins",
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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - plugins block - non-empty body`() {

        val pluginsProgram =
            Program.Plugins(fragment("plugins", "..."))

        val source =
            ProgramSource("build.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that applies plugin requests, base plugins then evaluates script body",
            partialEvaluationOf(
                Program.Staged(
                    pluginsProgram,
                    Program.Script(source)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(pluginsProgram),
                        ApplyBasePlugins,
                    ),
                    source
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript and plugins blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                null,
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", "..."))
            )

        assertThat(
            "reduces to static program that applies plugin requests then base plugins",
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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript and plugins blocks - non-empty body`() {

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
            "reduces to static program that applies plugin requests then base plugins then evaluates script body",
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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - declarative plugins block - empty body`() {

        val program =
            Program.Plugins(fragment("plugins", """id("plugin-id")"""))

        assertThat(
            "reduces to static program that applies declared plugin requests and base plugins",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    ApplyPluginRequests(
                        listOf(PluginRequestSpec("plugin-id")),
                        source = program
                    ),
                    ApplyBasePlugins
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - declarative plugins block - non-empty body`() {

        val pluginsProgram =
            Program.Plugins(fragment("plugins", """id("my-plugin")"""))

        val source =
            ProgramSource("build.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that applies declared plugin request then base plugins then evaluates script body",
            partialEvaluationOf(
                Program.Staged(
                    pluginsProgram,
                    Program.Script(source)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequests(
                            listOf(PluginRequestSpec("my-plugin")),
                            source = pluginsProgram
                        ),
                        ApplyBasePlugins,
                    ),
                    source
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript and declarative plugins blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                null,
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", """id("my-plugin")"""))
            )

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
                )
            )
        )
    }

    @Test
    fun `Project target - top-level - buildscript and declarative plugins blocks - non-empty body`() {

        val program =
            Program.Staged(
                Program.Stage1Sequence(
                    null,
                    Program.Buildscript(fragment("buildscript", "...")),
                    Program.Plugins(fragment("plugins", """id("my-plugin")"""))
                ),
                Program.Script(ProgramSource("script.gradle.kts", "..."))
            )

        assertThat(
            "reduces to dynamic program that applies plugin requests then base plugins then evaluates script body",
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
                )
            )
        )
    }

    @Test
    fun `Project target - script-plugin - empty`() {

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
                )
            )
        )
    }

    @Test
    fun `Project target - script-plugin - non-empty body`() {

        val source =
            ProgramSource("script-plugin.gradle.kts", "a script plugin")

        assertThat(
            "reduces to static program that closes target scope then applies base plugins then evaluates script body",
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
                )
            )
        )
    }

    @Test
    fun `Project target - script-plugin - buildscript block - empty body`() {

        val fragment = fragment("buildscript", "...")

        assertThat(
            "reduces to static program that collects build script dependencies then closes target scope",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    CollectProjectScriptDependencies(fragment.source),
                    CloseTargetScope
                )
            )
        )
    }

    @Test
    fun `Project target - script-plugin - buildscript block - non-empty body`() {

        val buildscriptFragment =
            fragment("buildscript", "...")

        val source =
            ProgramSource("build.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that collects project script dependencies then applies default plugin requests then base plugins",
            partialEvaluationOf(
                Program.Staged(
                    Program.Buildscript(buildscriptFragment),
                    Program.Script(source)
                ),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Project
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        CollectProjectScriptDependencies(buildscriptFragment.source),
                        ApplyDefaultPluginRequests,
                        ApplyBasePlugins
                    ),
                    source
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - empty`() {

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - non-empty body`() {

        val source =
            ProgramSource("settings.gradle.kts", "include(\"foo\", \"bar\")")

        assertThat(
            "reduces to static program that applies default plugin requests then closes target scope",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    ApplyDefaultPluginRequests,
                    Eval(source)
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement block - empty body`() {

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement block - non-empty body`() {

        val originalSource =
            ProgramSource(
                "settings.gradle.kts",
                "\npluginManagement { repositories {} }; include(\"stage-2\")"
            )

        val pluginManagementFragment =
            Program.PluginManagement(originalSource.fragment(1..15, 17..36))

        val scriptSource =
            originalSource.map { text("\n                               ; include(\"stage-2\")") }

        assertThat(
            "reduces to dynamic program that evaluates pluginManagement then script body",
            partialEvaluationOf(
                Program.Staged(
                    pluginManagementFragment,
                    Program.Script(scriptSource)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(pluginManagementFragment),
                    ),
                    scriptSource
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - buildscript block - empty body`() {

        val fragment =
            fragment("buildscript", "...")

        assertThat(
            "reduces to static program that evaluates buildscript block then applies default plugin requests",
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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - buildscript block - non-empty body`() {

        val originalSource =
            ProgramSource(
                "settings.gradle.kts",
                "\nbuildscript { dependencies {} }; include(\"stage-2\")"
            )

        val buildscriptFragment =
            originalSource.fragment(1..10, 12..31)

        val scriptSource =
            originalSource.map { text("\n                               ; include(\"stage-2\")") }

        val expectedEvalSource =
            originalSource.map { text("\nbuildscript { dependencies {} }") }

        assertThat(
            "reduces to dynamic program that evaluates buildscript block then applies default plugins then evaluates script body",
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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - plugins block - empty body`() {

        val program =
            Program.Plugins(fragment("plugins", "..."))

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - plugins block - non-empty body`() {

        val pluginsProgram =
            Program.Plugins(fragment("plugins", "..."))

        val source =
            ProgramSource("settings.gradle.kts", "...")

        assertThat(
            "reduces to dynamic program that applies plugin requests without base plugins then evaluates script body",
            partialEvaluationOf(
                Program.Staged(
                    pluginsProgram,
                    Program.Script(source)
                ),
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(pluginsProgram),
                    ),
                    source
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement and plugins blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                Program.PluginManagement(fragment("pluginManagement", "...")),
                null,
                Program.Plugins(fragment("plugins", "..."))
            )

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement and plugins blocks - non-empty body`() {

        val program =
            Program.Staged(
                Program.Stage1Sequence(
                    Program.PluginManagement(fragment("pluginManagement", "...")),
                    null,
                    Program.Plugins(fragment("plugins", "..."))
                ),
                Program.Script(ProgramSource("script.gradle.kts", "..."))
            )

        assertThat(
            "reduces to dynamic program that applies plugin requests without base plugins then evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                    ),
                    program.stage2.source
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement and buildscript blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                Program.PluginManagement(fragment("pluginManagement", "...")),
                Program.Buildscript(fragment("buildscript", "...")),
                null
            )

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement and buildscript blocks - non-empty body`() {

        val program =
            Program.Staged(
                Program.Stage1Sequence(
                    Program.PluginManagement(fragment("pluginManagement", "...")),
                    Program.Buildscript(fragment("buildscript", "...")),
                    null
                ),
                Program.Script(ProgramSource("script.gradle.kts", "..."))
            )

        assertThat(
            "reduces to dynamic program that applies plugin requests then evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                    ),
                    program.stage2.source
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - buildscript and plugins blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                null,
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", "..."))
            )

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - buildscript and plugins blocks - non-empty body`() {

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
            "reduces to dynamic program that applies plugin requests and evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                    ),
                    program.stage2.source
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement, buildscript and plugins blocks - empty body`() {

        val program =
            Program.Stage1Sequence(
                Program.PluginManagement(fragment("pluginManagement", "...")),
                Program.Buildscript(fragment("buildscript", "...")),
                Program.Plugins(fragment("plugins", "..."))
            )

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
                )
            )
        )
    }

    @Test
    fun `Settings target - top-level - pluginManagement, buildscript and plugins blocks - non-empty body`() {

        val program =
            Program.Staged(
                Program.Stage1Sequence(
                    Program.PluginManagement(fragment("pluginManagement", "...")),
                    Program.Buildscript(fragment("buildscript", "...")),
                    Program.Plugins(fragment("plugins", "..."))
                ),
                Program.Script(ProgramSource("script.gradle.kts", "..."))
            )

        assertThat(
            "reduces to dynamic program that applies plugin requests without base plugins then evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                    ),
                    program.stage2.source
                )
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - empty`() {

        assertThat(
            "reduces to static program that closes target scope",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(CloseTargetScope)
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - non-empty body`() {

        val source =
            ProgramSource("script-plugin.gradle.kts", "a script plugin")

        assertThat(
            "reduces to static program that closes target scope then evaluates script body",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope,
                    Eval(source)
                )
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - pluginManagement block - empty body`() {

        val fragment = fragment("buildscript", "...")

        assertThat(
            "reduces to static program that evaluates buildscript then closes target scope",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    CloseTargetScope,
                )
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - pluginManagement block - non-empty body`() {

        val pluginManagement = fragment("pluginManagement", "...")
        val body = ProgramSource("settings.gradle.kts", "...")

        val program = Program.Staged(
            Program.Stage1Sequence(Program.PluginManagement(pluginManagement), null, null),
            Program.Script(body)
        )

        assertThat(
            "reduces to dynamic program that applies plugin requests and evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1)
                    ),
                    body
                )
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - buildscript block - empty body`() {

        val fragment = fragment("buildscript", "...")

        assertThat(
            "reduces to static program that evaluates buildscript then closes target scope",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    CloseTargetScope,
                )
            )
        )
    }

    @Test
    fun `Settings target - script-plugin - buildscript block - non-empty body`() {

        val initscript = fragment("buildscript", "...")
        val body = ProgramSource("init.gradle.kts", "...")

        val program = Program.Staged(
            Program.Stage1Sequence(null, Program.Buildscript(initscript), null),
            Program.Script(body)
        )

        assertThat(
            "reduces to dynamic program that applies plugin requests and evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Settings
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1)
                    ),
                    body
                )
            )
        )
    }

    @Test
    fun `Gradle target - top-level - empty`() {

        assertThat(
            "reduces to static program that applies default plugin requests",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.TopLevel,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    ApplyDefaultPluginRequests
                )
            )
        )
    }

    @Test
    fun `Gradle target - top-level - non-empty body`() {

        val source = ProgramSource("init.gradle.kts", "dynamic")

        assertThat(
            "reduces to static program that applies default plugin requests and evaluates script body",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.TopLevel,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    ApplyDefaultPluginRequests,
                    Eval(source)
                )
            )
        )
    }

    @Test
    fun `Gradle target - top-level - initscript block - empty body`() {

        val fragment = fragment("initscript", "...")

        assertThat(
            "reduces to static program that evaluates initscript then applies default plugin requests",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.TopLevel,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    ApplyDefaultPluginRequests
                )
            )
        )
    }

    @Test
    fun `Gradle target - top-level - initscript block - non-empty body`() {

        val initscript = fragment("initscript", "...")
        val body = ProgramSource("init.gradle.kts", "...")

        val program = Program.Staged(
            Program.Stage1Sequence(null, Program.Buildscript(initscript), null),
            Program.Script(body)
        )

        assertThat(
            "reduces to dynamic program that applies plugin requests then evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.TopLevel,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1)
                    ),
                    body
                )
            )
        )
    }

    @Test
    fun `Gradle target - script-plugin - empty`() {

        assertThat(
            "reduces to static program that closes target scope",
            partialEvaluationOf(
                Program.Empty,
                ProgramKind.ScriptPlugin,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope
                )
            )
        )
    }

    @Test
    fun `Gradle target - script-plugin - non-empty body`() {

        val source = ProgramSource("init.gradle.kts", "dynamic")

        assertThat(
            "reduces to static program that closes target scope then evaluates script body",
            partialEvaluationOf(
                Program.Script(source),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    CloseTargetScope,
                    Eval(source)
                )
            )
        )
    }

    @Test
    fun `Gradle target - script-plugin - initscript block - empty body`() {

        val fragment = fragment("initscript", "...")

        assertThat(
            "reduces to static program that evaluates initscript then closes target scope",
            partialEvaluationOf(
                Program.Buildscript(fragment),
                ProgramKind.ScriptPlugin,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Static(
                    SetupEmbeddedKotlin,
                    Eval(fragment.source),
                    CloseTargetScope,
                )
            )
        )
    }

    @Test
    fun `Gradle target - script-plugin - initscript block - non-empty body`() {

        val initscript = fragment("initscript", "...")
        val body = ProgramSource("init.gradle.kts", "...")

        val program = Program.Staged(
            Program.Stage1Sequence(null, Program.Buildscript(initscript), null),
            Program.Script(body)
        )

        assertThat(
            "reduces to dynamic program that applies plugin requests then evaluates script body",
            partialEvaluationOf(
                program,
                ProgramKind.ScriptPlugin,
                ProgramTarget.Gradle
            ),
            isResidualProgram(
                Dynamic(
                    Static(
                        SetupEmbeddedKotlin,
                        ApplyPluginRequestsOf(program.stage1),
                    ),
                    body
                )
            )
        )
    }

    @Test
    fun `Stage1 only Program is unsupported`() {
        ProgramTarget.values().forEach { programTarget ->
            ProgramKind.values().forEach { programKind ->
                try {
                    partialEvaluationOf(object : Program.Stage1() {}, programKind, programTarget)
                    fail("Should have failed")
                } catch (ex: IllegalArgumentException) {
                    assertThat(ex.message, containsString("Program is unsupported"))
                }
            }
        }
    }

    private
    fun partialEvaluationOf(
        program: Program,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ): ResidualProgram = PartialEvaluator(programKind, programTarget).reduce(program)

    private
    fun isResidualProgram(program: ResidualProgram) =
        equalTo(program)
}
