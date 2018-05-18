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

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.initialization.ScriptHandlerInternal

import org.gradle.groovy.scripts.ScriptSource

import org.gradle.internal.classloader.ClasspathUtil.getClasspathForClass
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath

import org.gradle.kotlin.dsl.KotlinSettingsScript
import org.gradle.kotlin.dsl.fixtures.TestWithTempFiles
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf
import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.support.KotlinScriptHost

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.ByteArrayOutputStream
import java.io.PrintStream


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

        withExecutableProgramFor(Program.Script(source)) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)
            program.execute(programHost, scriptHost)

            inOrder(programHost) {
                verify(programHost).closeTargetScopeOf(scriptHost)
                verify(programHost).evaluateDynamicScriptOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = KotlinSettingsScript::class.qualifiedName!!,
                    // localClassPathHash = emptyHashCode, // only applicable once we have accessors
                    sourceHash = sourceHash)
            }

            program.loadScriptFor(programHost, scriptHost)

            val scriptFile = outputDir().resolve("settings.gradle.kts")
            verify(programHost).compileScriptOf(
                scriptHost,
                scriptPath = scriptFile.canonicalPath,
                originalPath = source.path,
                sourceHash = sourceHash)

            assertThat(
                scriptFile.readText(),
                equalTo(source.text))
        }
    }

    @Test
    fun `Stage 1 Settings program closes target scope after Stage 1 execution`() {

        val source = ProgramSource("settings.gradle.kts", "buildscript { repositories }")
        val fragment = source.fragment(0..10, 12..27)
        val scriptHandler = mock<ScriptHandlerInternal> {
            on { repositories } doReturn mock<RepositoryHandler>()
        }

        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(scriptHandler = scriptHandler)

        withExecutableProgramFor(Program.Buildscript(fragment)) {
            execute(programHost, scriptHost)
        }

        inOrder(programHost, scriptHandler) {
            verify(scriptHandler).repositories
            verify(programHost).closeTargetScopeOf(scriptHost)
        }
    }

    @Test
    fun `can compile staged Settings program`() {

        val source = ProgramSource(
            "settings.gradle.kts", """
            buildscript { println("stage 1"); repositories }
            print("stage 2")
        """.replaceIndent())

        val fragment = source.fragment(0..10, 12..47)
        val stage1 = Program.Buildscript(fragment)
        val stage2 = Program.Script(source.map { it.erase(listOf(fragment.section.wholeRange)) })

        val scriptHandler = mock<ScriptHandlerInternal> {
            on { repositories } doReturn mock<RepositoryHandler>()
        }
        val programHost = mock<ExecutableProgram.Host>()
        val scriptHost = scriptHostWith(scriptHandler = scriptHandler)

        withExecutableProgramFor(Program.Staged(stage1, stage2)) {

            val program = assertInstanceOf<ExecutableProgram.StagedProgram>(this)

            assertThat(
                standardOutputOf {
                    program.execute(programHost, scriptHost)
                },
                equalTo("stage 1\n"))

            inOrder(programHost, scriptHandler) {
                verify(scriptHandler).repositories
                verify(programHost).closeTargetScopeOf(scriptHost)
                verify(programHost).evaluateDynamicScriptOf(
                    program = program,
                    scriptHost = scriptHost,
                    scriptTemplateId = KotlinSettingsScript::class.qualifiedName!!,
                    sourceHash = scriptSourceHash(stage2.source.text))
            }
        }
    }

    private
    fun scriptHostWith(
        target: Settings = mock(),
        scriptSource: ScriptSource = mock(),
        scriptHandler: ScriptHandlerInternal = mock()
    ) = KotlinScriptHost(target, scriptSource, scriptHandler, mock(), mock(), mock())

    private
    fun <T> withExecutableProgramFor(program: Program, action: ExecutableProgram.() -> T): T =
        outputDir().let { outputDir ->
            ResidualProgramCompiler(outputDir, mock(), testCompilationClassPath).compile(program)
            classLoaderFor(outputDir).use { classLoader ->
                val executableProgram = classLoader.loadClass("Program").newInstance()
                action(executableProgram as ExecutableProgram)
            }
        }

    private
    fun outputDir() = root.resolve("classes").apply { mkdir() }
}


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
