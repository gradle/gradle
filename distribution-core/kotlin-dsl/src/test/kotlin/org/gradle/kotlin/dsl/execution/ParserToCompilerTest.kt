/*
 * Copyright 2019 the original author or authors.
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
import com.nhaarman.mockito_kotlin.mock
import org.gradle.internal.classpath.ClassPath
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.junit.Test


class ParserToCompilerTest : TestWithCompiler() {

    @Test
    fun `solo pluginManagement`() {
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1") }
            """.trimIndent()
        )
        assertStageOneOutput(source, "stage 1\n")
    }

    @Test
    fun `pluginManagement then buildscipt`() {
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1 pluginManagement") }
            buildscript { println("stage 1 buildscript") }
            """.trimIndent()
        )
        assertStageOneOutput(
            source,
            "stage 1 pluginManagement\nstage 1 buildscript\n"
        )
    }

    @Test
    fun `pluginManagement then plugins`() {
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1 pluginManagement") }
            plugins { println("stage 1 plugins") }
            """.trimIndent()
        )
        assertStageOneOutput(
            source,
            "stage 1 pluginManagement\nstage 1 plugins\n"
        )
    }

    @Test
    fun `buildscript then plugins`() {
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            """.trimIndent()
        )
        assertStageOneOutput(
            source,
            "stage 1 buildscript\nstage 1 plugins\n"
        )
    }

    @Test
    fun `pluginManagement then buildscript then plugins`() {
        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1 pluginManagement") }
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            """.trimIndent()
        )
        assertStageOneOutput(
            source,
            "stage 1 pluginManagement\nstage 1 buildscript\nstage 1 plugins\n"
        )
    }

    private
    fun assertStageOneOutput(source: ProgramSource, expectedStage1Output: String) {
        val program = parseToResidual(source, programTarget = ProgramTarget.Settings)

        val scriptHost = scriptHostWith(target = mockSettings())
        val accessorsClassPath = mock<ClassPath>()
        val programHost = safeMockProgramHost {
            on { accessorsClassPathFor(scriptHost) } doReturn accessorsClassPath
        }

        withExecutableProgramFor(program) {
            assertStandardOutputOf(expectedStage1Output) {
                execute(programHost, scriptHost)
            }
        }
    }

    private
    fun parseToResidual(
        source: ProgramSource,
        programKind: ProgramKind = ProgramKind.TopLevel,
        programTarget: ProgramTarget = ProgramTarget.Project
    ): ResidualProgram {
        val program = ProgramParser.parse(source, programKind, programTarget)
        return PartialEvaluator(programKind, programTarget).reduce(program.document)
    }
}
