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

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argThat
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.verify
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ScriptHandlerInternal
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.hash.TestHashCodes
import org.gradle.kotlin.dsl.execution.ResidualProgram.Dynamic
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyBasePlugins
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyDefaultPluginRequests
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyPluginRequests
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.ApplyPluginRequestsOf
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.CloseTargetScope
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.Eval
import org.gradle.kotlin.dsl.execution.ResidualProgram.Instruction.SetupEmbeddedKotlin
import org.gradle.kotlin.dsl.execution.ResidualProgram.Static
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.gradle.plugin.management.internal.PluginRequests
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.mockito.InOrder
import java.util.Arrays.fill


class ResidualProgramCompilerTest : TestWithCompiler() {

    @Test
    fun `Static(CloseTargetScope)`() {

        withExecutableProgramFor(Static(CloseTargetScope)) {

            val programHost = safeMockProgramHost()
            val scriptHost = scriptHostWith()

            execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).closeTargetScopeOf(scriptHost)
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Static(SetupEmbeddedKotlin)`() {

        withExecutableProgramFor(Static(SetupEmbeddedKotlin)) {

            val programHost = mock<ExecutableProgram.Host>()
            val scriptHost = scriptHostWith()

            execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).setupEmbeddedKotlinFor(scriptHost)
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Static(CloseTargetScope, Eval(source))`() {

        val source = ProgramSource(
            "plugin.settings.gradle.kts",
            "include(\"precompiled stage 2\")"
        )

        val target = mock<Settings>()
        val programHost = safeMockProgramHost()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(Static(CloseTargetScope, Eval(source))) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {
            verify(programHost).closeTargetScopeOf(scriptHost)
            verify(target).include("precompiled stage 2")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Static(CloseTargetScope, ApplyBasePlugins)`() {

        val target = mock<Project>()
        val programHost = safeMockProgramHost()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(
            Static(CloseTargetScope, ApplyBasePlugins),
            programKind = ProgramKind.ScriptPlugin,
            programTarget = ProgramTarget.Project
        ) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {

            verify(programHost).closeTargetScopeOf(scriptHost)
            verify(programHost).applyBasePluginsTo(target)

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Dynamic(Static(CloseTargetScope))`() {

        val source = ProgramSource("settings.gradle.kts", "include(\"foo\", \"bar\")")
        val sourceHash = TestHashCodes.hashCodeFrom(42)
        val target = mock<Settings>()
        val programHost = safeMockProgramHost()
        val scriptHost = scriptHostWith(target)
        val stage2ProgramId = ProgramId(stage2SettingsTemplateId, sourceHash, mock())

        withExecutableProgramFor(
            Dynamic(Static(CloseTargetScope), source),
            sourceHash = sourceHash
        ) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)
            program.execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).closeTargetScopeOf(scriptHost)
                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = stage2SettingsTemplateId,
                    // localClassPathHash = emptyHashCode, // only applicable once we have accessors
                    sourceHash = sourceHash,
                    accessorsClassPath = ClassPath.EMPTY
                )
            }

            program.loadSecondStageFor(
                programHost,
                scriptHost,
                stage2ProgramId,
                ClassPath.EMPTY
            )

            verify(programHost).compileSecondStageOf(
                program,
                scriptHost,
                stage2ProgramId,
                ProgramKind.TopLevel,
                ProgramTarget.Settings,
                ClassPath.EMPTY
            )
        }
    }

    @Test
    fun `Dynamic(Static(ApplyDefaultPluginRequests, ApplyBasePlugins))`() {

        val source = ProgramSource(
            "build.gradle.kts",
            "task(\"precompiled stage 2\")"
        )

        val sourceHash = TestHashCodes.hashCodeFrom(42)
        val target = mock<Project>()
        val scriptHost = scriptHostWith(target)
        val accessorsClassPath = mock<ClassPath>()
        val programHost = safeMockProgramHost {
            on { accessorsClassPathFor(scriptHost) } doReturn accessorsClassPath
        }

        withExecutableProgramFor(
            Dynamic(Static(ApplyDefaultPluginRequests, ApplyBasePlugins), source),
            programKind = ProgramKind.TopLevel,
            programTarget = ProgramTarget.Project,
            sourceHash = sourceHash
        ) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)
            execute(programHost, scriptHost)

            inOrder(programHost, target) {

                verify(programHost).applyPluginsTo(scriptHost, PluginRequests.EMPTY)
                verify(programHost).applyBasePluginsTo(target)
                verify(programHost).evaluateSecondStageOf(
                    program,
                    scriptHost,
                    "Project/TopLevel/stage2",
                    sourceHash,
                    accessorsClassPath
                )

                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Dynamic(Static(Eval(buildscript), CloseTargetScope))`() {

        val buildscriptFragment = fragment(
            "buildscript",
            "println(\"stage 1\"); repositories"
        )

        val scriptSource =
            buildscriptFragment.source.map { text("println(\"stage 2\")") }

        val sourceHash = TestHashCodes.hashCodeFrom(42)

        val scriptHandler = mock<ScriptHandlerInternal> {
            on { repositories } doReturn mock<RepositoryHandler>()
        }

        val programHost = safeMockProgramHost()

        val scriptHost = scriptHostWith(
            target = mock<Settings>(),
            scriptHandler = scriptHandler
        )

        withExecutableProgramFor(
            Dynamic(
                Static(
                    Eval(buildscriptFragment.source),
                    CloseTargetScope
                ),
                scriptSource
            ),
            sourceHash
        ) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)

            assertStandardOutputOf("stage 1\n") {
                program.execute(programHost, scriptHost)
            }

            inOrder(programHost, scriptHandler) {
                verify(scriptHandler).repositories
                verify(programHost).closeTargetScopeOf(scriptHost)
                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = stage2SettingsTemplateId,
                    sourceHash = sourceHash,
                    accessorsClassPath = ClassPath.EMPTY
                )
            }
        }
    }

    @Test
    fun `Static(ApplyPluginRequestsOf(plugins), ApplyBasePlugins)`() {
        val fragment = fragmentAtLine(
            3,
            "plugins",
            """id("stage-1")"""
        )
        assertPluginApplicationBehaviorOf(
            fragment,
            ApplyPluginRequestsOf(Program.Plugins(fragment))
        )
    }

    @Test
    fun `Static(ApplyPluginRequests(plugins), ApplyBasePlugins)`() {
        val fragment = fragmentAtLine(
            3,
            "plugins",
            """id("stage-1")"""
        )
        assertPluginApplicationBehaviorOf(
            fragment,
            ApplyPluginRequests(
                listOf(ResidualProgram.PluginRequestSpec("stage-1")),
                source = Program.Plugins(fragment)
            )
        )
    }

    private
    fun assertPluginApplicationBehaviorOf(fragment: ProgramSourceFragment, applyPlugins: ResidualProgram.Instruction) {
        val target = mock<Project>()

        val scriptHost = scriptHostWith(target = target)

        val capturedPluginRequests = mutableListOf<PluginRequests>()

        val programHost = safeMockProgramHost {

            on { applyPluginsTo(same(scriptHost), any()) } doAnswer {

                capturedPluginRequests.add(it.getArgument(1))
                Unit
            }
        }

        withExecutableProgramFor(
            Static(
                applyPlugins,
                ApplyBasePlugins
            ),
            programTarget = ProgramTarget.Project
        ) {

            execute(programHost, scriptHost)

            inOrder(programHost) {

                verify(programHost).applyPluginsTo(same(scriptHost), any())

                verify(programHost).applyBasePluginsTo(target)

                verifyNoMoreInteractions()
            }
        }

        val pluginRequests = capturedPluginRequests.single()

        assertThat(
            pluginRequests.asIterable().map { it.toString() },
            equalTo(listOf("[id: 'stage-1']"))
        )

        assertThat(
            pluginRequests.single().lineNumber,
            equalTo(fragment.lineNumber)
        )
    }

    @Test
    fun `Dynamic(Static(ApplyPluginRequestsOf(plugins), ApplyBasePlugins))`() {

        val source = ProgramSource(
            "build.gradle.kts",
            """
                plugins { println("stage 1") }
                print("stage 2")
            """.replaceIndent()
        )

        val fragment = source.fragment(0..6, 8..29)
        val stage1 = Program.Plugins(fragment)
        val stage2 = source.map { it.erase(listOf(fragment.range)) }
        val stagedProgram = Dynamic(
            Static(ApplyPluginRequestsOf(stage1), ApplyBasePlugins),
            stage2
        )

        assertStagedTopLevelProjectProgram(
            stagedProgram,
            "stage 1\n"
        )
    }

    @Test
    fun `Dynamic(Static(Eval(buildscript), ApplyDefaultPluginRequests, ApplyBasePlugins))`() {

        val fragment =
            fragment("buildscript", """println("stage 1")""")

        val stage2 =
            fragment.source.map {
                text("""println("stage 2")""")
            }

        val stagedProgram =
            Dynamic(
                Static(
                    Eval(fragment.source),
                    ApplyDefaultPluginRequests,
                    ApplyBasePlugins
                ),
                stage2
            )

        assertStagedTopLevelProjectProgram(
            stagedProgram,
            "stage 1\n"
        )
    }

    @Test
    fun `Dynamic(Static(ApplyPluginsRequestsOf(Stage1Sequence), ApplyBasePlugins))`() {

        val source = ProgramSource(
            "build.gradle.kts",
            """
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            print("stage 2")
            """.replaceIndent()
        )

        val buildscript = Program.Buildscript(source.fragment(0..10, 12..45))
        val plugins = Program.Plugins(source.fragment(47..52, 54..84))
        val stage1 = Program.Stage1Sequence(null, buildscript, plugins)
        val stage2 = source.map { it.without(buildscript, plugins) }
        val stagedProgram =
            Dynamic(
                Static(
                    ApplyPluginRequestsOf(stage1),
                    ApplyBasePlugins
                ),
                stage2
            )

        assertStagedTopLevelProjectProgram(
            stagedProgram,
            expectedStage1Output = "stage 1 buildscript\nstage 1 plugins\n"
        )
    }

    @Test
    fun `Dynamic(Static(Eval(pluginManagement, CloseTargetScope)))`() {
        val fragment =
            fragment("pluginManagement", """println("stage 1")""")

        val stage2 =
            fragment.source.map {
                text("""println("stage 2")""")
            }

        val stagedProgram =
            Dynamic(
                Static(
                    Eval(fragment.source),
                    CloseTargetScope
                ),
                stage2
            )

        assertStagedTopLevelSettingsProgram(stagedProgram, "stage 1\n")
    }

    @Test
    fun `Static(ApplyPluginRequestsOf(Stage1Sequence(pluginManagement, plugins))`() {
        // Given
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1 pluginManagement") }
            plugins { println("stage 1 plugins") }
            """.replaceIndent()
        )
        val pluginManagement = Program.PluginManagement(source.fragment(0..15, 17..55))
        val plugins = Program.Plugins(source.fragment(57..62, 64..94))
        val stage1 = Program.Stage1Sequence(pluginManagement, null, plugins)

        // When
        val stagedProgram =
            Static(
                SetupEmbeddedKotlin,
                ApplyPluginRequestsOf(stage1)
            )

        // Then
        val target = mockSettings()
        TopLevelProgramVerifier(
            stagedProgram,
            "stage 1 pluginManagement\nstage 1 plugins\n",
            target,
            ProgramTarget.Settings
        ).apply {
            verifyInOrder {
                verify(programHost).applyPluginsTo(any(), any())

                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Static(Eval(buildscript)) reports script exception back to host`() {

        val fragment = fragment(
            "buildscript",
            "throw IllegalStateException(\"BOOM!\")"
        )

        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(mock<Settings>())
        withExecutableProgramFor(Static(Eval(fragment.source))) {

            val program = this
            program.execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).handleScriptException(
                    exception = any<IllegalStateException>(),
                    scriptClass = same(program.javaClass.classLoader.loadClass("Buildscript_gradle")),
                    scriptHost = same(scriptHost)
                )
            }
        }
    }

    private
    fun ProgramText.without(buildscript: Program.Buildscript, plugins: Program.Plugins) =
        erase(listOf(buildscript.fragment, plugins.fragment).map { it.range })

    private
    fun assertStagedTopLevelSettingsProgram(
        stagedProgram: ResidualProgram,
        expectedStage1Output: String
    ) {
        val target = mockSettings()
        TopLevelProgramVerifier(
            stagedProgram,
            expectedStage1Output,
            target,
            ProgramTarget.Settings
        ).apply {
            verifyStagedInOrder { program ->
                verify(programHost).closeTargetScopeOf(scriptHost)

                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = stage2SettingsTemplateId,
                    // localClassPathHash = emptyHashCode, // only applicable once we have accessors
                    sourceHash = sourceHash,
                    accessorsClassPath = ClassPath.EMPTY
                )

                verify(programHost).compileSecondStageOf(
                    same(program),
                    same(scriptHost),
                    argThat { this.sourceHash == sourceHash && this.templateId == scriptTemplateId },
                    same(ProgramKind.TopLevel),
                    same(programTarget),
                    same(accessorsClassPath)
                )

                verifyNoMoreInteractions()
            }
        }
    }

    private
    fun assertStagedTopLevelProjectProgram(
        stagedProgram: Dynamic,
        expectedStage1Output: String
    ) {
        val target = mock<Project>()
        TopLevelProgramVerifier(
            stagedProgram,
            expectedStage1Output,
            target,
            ProgramTarget.Project
        ).apply {
            verifyStagedInOrder { program ->
                verify(programHost).applyPluginsTo(
                    scriptHost,
                    PluginRequests.EMPTY
                )

                verify(programHost).applyBasePluginsTo(target)

                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = scriptTemplateId,
                    sourceHash = sourceHash,
                    accessorsClassPath = accessorsClassPath
                )

                verify(programHost).compileSecondStageOf(
                    same(program),
                    same(scriptHost),
                    argThat { this.sourceHash == sourceHash && this.templateId == scriptTemplateId },
                    same(ProgramKind.TopLevel),
                    same(programTarget),
                    same(accessorsClassPath)
                )

                verifyNoMoreInteractions()
            }
        }
    }

    private
    inner class TopLevelProgramVerifier<T : Any>(
        val stagedProgram: ResidualProgram,
        val expectedStage1Output: String,
        target: T,
        val programTarget: ProgramTarget
    ) {
        val sourceHash = TestHashCodes.hashCodeFrom(42)
        val scriptHost = scriptHostWith(target = target)
        val accessorsClassPath = mock<ClassPath>()
        val programHost = safeMockProgramHost {
            on { accessorsClassPathFor(scriptHost) } doReturn accessorsClassPath
        }

        val scriptTemplateId = "Project/TopLevel/stage2"
        val programId = ProgramId(scriptTemplateId, sourceHash, mock())

        private
        fun verifyStandardOutput(program: ExecutableProgram) {
            assertStandardOutputOf(expectedStage1Output) {
                program.execute(programHost, scriptHost)
                if (program is ExecutableProgram.StagedProgram) {
                    program.loadSecondStageFor(programHost, scriptHost, programId, accessorsClassPath)
                }
            }
        }

        fun verifyInOrder(inOrderBlock: InOrder.(program: ExecutableProgram) -> Unit) {
            withExecutableProgramFor(stagedProgram, sourceHash, programTarget = programTarget) {

                val program = this

                verifyStandardOutput(program)

                inOrder(programHost) {
                    inOrderBlock(program)
                }
            }
        }

        fun verifyStagedInOrder(inOrderBlock: InOrder.(program: ExecutableProgram.StagedProgram) -> Unit) {
            withExecutableProgramFor(stagedProgram, sourceHash, programTarget = programTarget) {

                val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)

                verifyStandardOutput(program)

                if (stagedProgram is Dynamic) {
                    assertThat(
                        program.secondStageScriptText,
                        equalTo(stagedProgram.source.text)
                    )
                }

                inOrder(programHost) {
                    inOrderBlock(program)
                }
            }
        }
    }

    private
    val stage2SettingsTemplateId = "Settings/TopLevel/stage2"
}


/**
 * Creates a text fragment placed at the given 1-based [lineNumber].
 */
internal
fun fragmentAtLine(lineNumber: Int, identifier: String, body: String): ProgramSourceFragment {
    require(lineNumber >= 1) { "Line numbers are 1-based" }
    return fragment(identifier, body, prefix = String(repeat('\n', lineNumber - 1)))
}


private
fun repeat(char: Char, times: Int) =
    CharArray(times).also { fill(it, char) }


internal
fun fragment(identifier: String, body: String, prefix: String = ""): ProgramSourceFragment {
    val source = ProgramSource("$identifier.gradle.kts", "$prefix$identifier { $body }")
    val identifierStart = prefix.length
    val identifierEnd = (identifierStart + identifier.lastIndex)
    return source.fragment(
        identifierStart..identifierEnd,
        (identifierEnd + 2)..source.text.lastIndex
    )
}
