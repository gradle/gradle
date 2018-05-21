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

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class PartialEvaluatorTest {

    @Test
    fun `empty source reduces to empty program`() {

        assertEmptyProgram("")
        assertEmptyProgram(" // An empty program\n")
        assertEmptyProgram("  \r\n// foo\r\n/* block comment */ ")
    }

    @Test
    fun `empty Stage 1 and empty Stage 2 reduce to empty program`() {

        assertEmptyProgram("buildscript {}")
        assertEmptyProgram("plugins {}")
        assertEmptyProgram("buildscript {}\r\nplugins {}")
        assertEmptyProgram(" /* before */buildscript { /* within */ }/* after */ ")
    }

    @Test
    fun `empty Stage 1 reduces to Stage 2 with Stage 1 fragments erased`() {

        val scriptOnlySource =
            programSourceWith("println(\"Stage 2\")")

        assertProgramOf(
            scriptOnlySource,
            Program.Script(scriptOnlySource))

        val emptyBuildscriptSource =
            programSourceWith("buildscript { }\nprintln(\"Stage 2\")")

        assertProgramOf(
            emptyBuildscriptSource,
            Program.Script(
                emptyBuildscriptSource.map {
                    text("               \nprintln(\"Stage 2\")")
                }))
    }

    @Test
    fun `empty Stage 2 reduces to Stage 1`() {

        val source = programSourceWith(" buildscript { println(\"Stage 1\") } ")

        assertProgramOf(
            source,
            Program.Buildscript(source.fragment(1..11, 13..34)))
    }

    @Test
    fun `given Stage 1 and Stage 2 are present, it reduces to Stage 1 followed by Stage 2`() {

        val source = ProgramSource(
            "/src/fragment.gradle.kts",
            "\r\n\r\nplugins {\r\n  java\r\n}\r\nprintln(\"Stage 2\")\r\n\r\n")

        assertProgramOf(
            source,
            Program.Staged(
                Program.Plugins(source.fragment(2..8, 10..19)),
                Program.Script(source.map { text("\n\n         \n      \n \nprintln(\"Stage 2\")") })))
    }

    @Test
    fun `Stage 2 only script plugin reduces to PrecompiledScript`() {

        val source = programSourceWith("println(\"no stage 1\")")
        assertProgramOf(
            programKind = ProgramKind.ScriptPlugin,
            source = source,
            expected = Program.PrecompiledScript(source))
    }

    private
    fun assertEmptyProgram(contents: String) {
        assertProgramOf(programSourceWith(contents), Program.Empty)
    }

    private
    fun assertProgramOf(source: ProgramSource, expected: Program, programKind: ProgramKind = ProgramKind.TopLevel) {
        val program = PartialEvaluator.reduce(source, programKind)
        assertThat(program, equalTo(expected))
    }

    private
    fun programSourceWith(contents: String) =
        ProgramSource("/src/build.gradle.kts", contents)
}
