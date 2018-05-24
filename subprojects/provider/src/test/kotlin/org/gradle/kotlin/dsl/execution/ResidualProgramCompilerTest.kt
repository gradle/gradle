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
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classloader.ClasspathUtil.getClasspathForClass
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.hash.HashCode

import org.gradle.kotlin.dsl.KotlinSettingsScript

import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.fixtures.equalToMultiLineString

import org.gradle.kotlin.dsl.support.KotlinScriptHost

import org.gradle.plugin.management.internal.DefaultPluginRequests

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

import java.lang.IllegalStateException


class ResidualProgramCompilerTest : TestWithTempFiles() {

    @Test
    fun `Empty Program compiles down to Empty ExecutableProgram`() {

        withExecutableProgramFor(Program.Empty) {
            assertInstanceOf<ExecutableProgram.Empty>(this)
        }
    }

    @Test
    fun `Empty program closes target scope`() {

        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(mock())

        ExecutableProgram.Empty().execute(programHost, scriptHost)

        verify(programHost).closeTargetScopeOf(scriptHost)
    }

    @Test
    fun `Stage 2 Settings program closes target scope before evaluateScriptOf`() {

        val source = ProgramSource("settings.gradle.kts", "include(\"foo\", \"bar\")")
        val sourceHash = scriptSourceHash(source.text)
        val target = mock<Settings>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(Program.Script(source), sourceHash = sourceHash) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)
            program.execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).closeTargetScopeOf(scriptHost)
                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = stage2TemplateId,
                    // localClassPathHash = emptyHashCode, // only applicable once we have accessors
                    sourceHash = sourceHash)
            }

            program.loadSecondStageFor(
                programHost,
                scriptHost,
                stage2TemplateId,
                sourceHash)

            val scriptFile = outputDir().resolve("settings.gradle.kts")
            verify(programHost).compileSecondStageScript(
                scriptFile.canonicalPath,
                source.path,
                scriptHost,
                stage2TemplateId,
                sourceHash,
                ProgramKind.TopLevel,
                ProgramTarget.Settings)

            assertThat(
                scriptFile.readText(),
                equalTo(source.text))
        }
    }

    @Test
    fun `Stage 1 Settings program closes target scope after Stage 1 execution`() {

        val source = ProgramSource("settings.gradle.kts", "buildscript { repositories }")
        val fragment = source.fragment(0..10, 12..source.text.lastIndex)
        val scriptHandler = mock<ScriptHandlerInternal> {
            on { repositories } doReturn mock<RepositoryHandler>()
        }

        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(mock<Settings>(), scriptHandler = scriptHandler)

        withExecutableProgramFor(Program.Buildscript(fragment)) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, scriptHandler) {
            verify(scriptHandler).repositories
            verify(programHost).closeTargetScopeOf(scriptHost)
        }
    }

    @Test
    fun `PrecompiledScript executes as precompiled script on Settings target`() {

        val source =
            ProgramSource(
                "plugin.settings.gradle.kts",
                "include(\"precompiled stage 2\")")

        val target = mock<Settings>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(Program.PrecompiledScript(source)) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {
            verify(programHost).closeTargetScopeOf(scriptHost)
            verify(target).include("precompiled stage 2")
        }
    }

    @Test
    fun `PrecompiledScript executes as precompiled script on Project target`() {

        val source =
            ProgramSource(
                "plugin.gradle.kts",
                "task(\"precompiled stage 2\")")

        val target = mock<Project>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(
            Program.PrecompiledScript(source),
            programKind = ProgramKind.ScriptPlugin,
            programTarget = ProgramTarget.Project
        ) {

            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {
            verify(programHost).closeTargetScopeOf(scriptHost)
            verify(programHost).applyBasePluginsTo(target)
            verify(target).task("precompiled stage 2")

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `TopLevel Project Script program must apply plugins and base plugins`() {

        val source =
            ProgramSource(
                "build.gradle.kts",
                "task(\"precompiled stage 2\")")

        val sourceHash = scriptSourceHash(source.text)
        val target = mock<Project>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(
            Program.Script(source),
            programKind = ProgramKind.TopLevel,
            programTarget = ProgramTarget.Project,
            sourceHash = sourceHash
        ) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)
            execute(programHost, scriptHost)

            inOrder(programHost, target) {

                verify(programHost).applyPluginsTo(scriptHost, DefaultPluginRequests.EMPTY)
                verify(programHost).applyBasePluginsTo(target)
                verify(programHost).evaluateSecondStageOf(
                    program,
                    scriptHost,
                    "Project/TopLevel/stage2",
                    sourceHash)

                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Empty TopLevel Project program must apply plugins and base plugins`() {

        val target = mock<Project>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(Program.Empty, programKind = ProgramKind.TopLevel, programTarget = ProgramTarget.Project) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {

            verify(programHost).applyPluginsTo(scriptHost, DefaultPluginRequests.EMPTY)
            verify(programHost).applyBasePluginsTo(target)

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Empty ScriptPlugin Project program must apply base plugins`() {

        val target = mock<Project>()
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target)

        withExecutableProgramFor(Program.Empty, programKind = ProgramKind.ScriptPlugin, programTarget = ProgramTarget.Project) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, target) {

            verify(programHost).closeTargetScopeOf(scriptHost)
            verify(programHost).applyBasePluginsTo(target)

            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `can compile Plugins program`() {

        val source = ProgramSource("build.gradle.kts", """plugins { println("stage 1") }""")
        val fragment = source.fragment(0..6, 8..source.text.lastIndex)
        val sourceHash = scriptSourceHash(source.text)

        val programHost = mock<ExecutableProgram.Host>()
        val target = mock<Project>()
        val scriptHost = scriptHostWith(target = target)

        withExecutableProgramFor(Program.Plugins(fragment), sourceHash, programTarget = ProgramTarget.Project) {

            assertStandardOutputOf("stage 1\n") {
                execute(programHost, scriptHost)
            }

            inOrder(programHost) {

                verify(programHost).applyPluginsTo(
                    scriptHost,
                    DefaultPluginRequests.EMPTY)

                verify(programHost).applyBasePluginsTo(target)

                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `can compile staged top-level Project program with a plugins block`() {

        val source = ProgramSource("build.gradle.kts", """
            plugins { println("stage 1") }
            print("stage 2")
        """.replaceIndent())

        val fragment = source.fragment(0..6, 8..29)
        val stage1 = Program.Plugins(fragment)
        val stage2 = Program.Script(source.map { it.erase(listOf(fragment.section.wholeRange)) })
        val stagedProgram = Program.Staged(stage1, stage2)

        assertStagedTopLevelProjectProgram(source, stagedProgram, "stage 1\n")
    }

    @Test
    fun `can compile staged top-level Project program with a buildscript block`() {

        val source = ProgramSource("build.gradle.kts", """
            buildscript { println("stage 1") }
            print("stage 2")
        """.replaceIndent())

        val fragment = source.fragment(0..10, 12..33)
        val stage1 = Program.Buildscript(fragment)
        val stage2 = Program.Script(source.map { it.erase(listOf(fragment.section.wholeRange)) })
        val stagedProgram = Program.Staged(stage1, stage2)

        assertStagedTopLevelProjectProgram(source, stagedProgram, "stage 1\n")
    }

    @Test
    fun `can compile staged top-level Project program with a buildscript followed by a plugins block`() {

        val source = ProgramSource("build.gradle.kts", """
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            print("stage 2")
        """.replaceIndent())

        val buildscript = Program.Buildscript(source.fragment(0..10, 12..45))
        val plugins = Program.Plugins(source.fragment(47..52, 54..84))
        val stage1 = Program.Stage1Sequence(buildscript, plugins)
        val stage2 = Program.Script(source.map { it.without(buildscript, plugins) })
        val stagedProgram = Program.Staged(stage1, stage2)

        assertStagedTopLevelProjectProgram(
            source,
            stagedProgram,
            expectedStage1Output = "stage 1 buildscript\nstage 1 plugins\n")
    }

    private
    fun ProgramText.without(buildscript: Program.Buildscript, plugins: Program.Plugins) =
        erase(listOf(buildscript.fragment, plugins.fragment).map { it.section.wholeRange })

    private
    fun assertStagedTopLevelProjectProgram(
        source: ProgramSource,
        stagedProgram: Program.Staged,
        expectedStage1Output: String
    ) {

        val sourceHash = scriptSourceHash(source.text)
        val programHost = mock<ExecutableProgram.Host>()
        val target = mock<Project>()
        val scriptHost = scriptHostWith(target = target)

        withExecutableProgramFor(stagedProgram, sourceHash, programTarget = ProgramTarget.Project) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)

            val scriptTemplateId = "Project/TopLevel/stage2"

            assertStandardOutputOf(expectedStage1Output) {
                program.execute(programHost, scriptHost)
                program.loadSecondStageFor(programHost, scriptHost, scriptTemplateId, sourceHash)
            }

            inOrder(programHost) {

                verify(programHost).applyPluginsTo(
                    scriptHost,
                    DefaultPluginRequests.EMPTY)

                verify(programHost).applyBasePluginsTo(target)

                verify(programHost).evaluateSecondStageOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = scriptTemplateId,
                    sourceHash = sourceHash)

                val scriptFile = outputDir().resolve(source.path)
                verify(programHost).compileSecondStageScript(
                    scriptFile.canonicalPath,
                    source.path,
                    scriptHost,
                    scriptTemplateId,
                    sourceHash,
                    ProgramKind.TopLevel,
                    ProgramTarget.Project)

                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `can compile staged Settings program`() {

        val source = ProgramSource("settings.gradle.kts", """
            buildscript { println("stage 1"); repositories }
            print("stage 2")
        """.replaceIndent())

        val fragment = source.fragment(0..10, 12..47)
        val stage1 = Program.Buildscript(fragment)
        val stage2 = Program.Script(source.map { it.erase(listOf(fragment.section.wholeRange)) })
        val stagedProgram = Program.Staged(stage1, stage2)

        val sourceHash = scriptSourceHash(source.text)

        val scriptHandler = mock<ScriptHandlerInternal> {
            on { repositories } doReturn mock<RepositoryHandler>()
        }
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(target = mock<Settings>(), scriptHandler = scriptHandler)

        withExecutableProgramFor(stagedProgram, sourceHash) {

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
                    scriptTemplateId = stage2TemplateId,
                    sourceHash = sourceHash)
            }
        }
    }

    @Test
    fun `Stage 1 program reports script exception back to host`() {

        val source =
            ProgramSource(
                "settings.gradle.kts",
                "buildscript { throw IllegalStateException(\"BOOM!\") }")

        val fragment =
            source.fragment(0..10, 12..source.text.lastIndex)

        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(mock<Settings>())
        withExecutableProgramFor(Program.Buildscript(fragment)) {

            val program = this
            program.execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).handleScriptException(
                    exception = any<IllegalStateException>(),
                    scriptClass = same(program.javaClass.classLoader.loadClass("Settings_gradle")),
                    scriptHost = same(scriptHost))
            }
        }
    }

    private
    fun scriptHostWith(
        target: Any = mock(),
        scriptSource: ScriptSource = mock(),
        scriptHandler: ScriptHandlerInternal = mock()
    ) = KotlinScriptHost(target, scriptSource, scriptHandler, mock(), mock(), mock())

    private
    fun <T> withExecutableProgramFor(
        program: Program,
        sourceHash: HashCode = HashCode.fromInt(0),
        programKind: ProgramKind = ProgramKind.TopLevel,
        programTarget: ProgramTarget = ProgramTarget.Settings,
        action: ExecutableProgram.() -> T
    ): T =

        outputDir().let { outputDir ->
            compileProgramTo(outputDir, program, sourceHash, programKind, programTarget)
            classLoaderFor(outputDir).use { classLoader ->
                val executableProgram = classLoader.loadClass("Program").newInstance()
                action(executableProgram as ExecutableProgram)
            }
        }

    private
    fun compileProgramTo(
        outputDir: File,
        program: Program,
        sourceHash: HashCode,
        programKind: ProgramKind,
        programTarget: ProgramTarget
    ) {
        ResidualProgramCompiler(
            outputDir,
            testCompilationClassPath,
            sourceHash,
            programKind,
            programTarget
        ).compile(program)
    }

    private
    val stage2TemplateId = "Settings/TopLevel/stage2"

    private
    fun outputDir() = root.resolve("classes").apply { mkdir() }
}


internal
fun assertStandardOutputOf(expected: String, action: () -> Unit): Unit =
    assertThat(
        standardOutputOf(action),
        equalToMultiLineString(expected))


internal
fun standardOutputOf(action: () -> Unit): String =
    ByteArrayOutputStream().also {
        val out = System.out
        try {
            System.setOut(PrintStream(it, true))
            action()
        } finally {
            System.setOut(out)
        }
    }.toString("utf8")


internal
val testCompilationClassPath: ClassPath by lazy {
    DefaultClassPath.of(
        getClasspathForClass(Unit::class.java),
        getClasspathForClass(Settings::class.java),
        getClasspathForClass(KotlinSettingsScript::class.java))
}
