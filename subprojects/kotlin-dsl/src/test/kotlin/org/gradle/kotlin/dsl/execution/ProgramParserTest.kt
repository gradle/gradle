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


class ProgramParserTest {

    @Test
    fun `empty source parses to empty program`() {

        assertEmptyProgram("")
        assertEmptyProgram(" // An empty program\n")
        assertEmptyProgram("  \r\n// foo\r\n/* block comment */ ")
    }

    @Test
    fun `empty Stage 1 with empty Stage 2 parse to empty program`() {

        assertEmptyProgram("pluginManagement {}", programTarget = ProgramTarget.Settings)
        assertEmptyProgram("plugins {}", programTarget = ProgramTarget.Settings)
        assertEmptyProgram("initscript {}", programTarget = ProgramTarget.Gradle)
        assertEmptyProgram("buildscript {}")
        assertEmptyProgram("plugins {}")
        assertEmptyProgram("buildscript {}\r\nplugins {}")
        assertEmptyProgram(" /* before */buildscript { /* within */ }/* after */ ")
    }

    @Test
    fun `empty Stage 1 with non-empty Stage 2 parse to Stage 2 with Stage 1 fragments erased`() {

        val scriptOnlySource =
            programSourceWith("println(\"Stage 2\")")

        assertProgramOf(
            scriptOnlySource,
            Program.Script(scriptOnlySource)
        )

        val emptyBuildscriptSource =
            programSourceWith("buildscript { }\nprintln(\"Stage 2\")")

        assertProgramOf(
            emptyBuildscriptSource,
            Program.Script(
                emptyBuildscriptSource.map {
                    text("               \nprintln(\"Stage 2\")")
                }
            )
        )
    }

    @Test
    fun `non-empty Stage 1 with empty Stage 2 parse to Stage 1`() {

        val source = programSourceWith(" buildscript { println(\"Stage 1\") } ")

        assertProgramOf(
            source,
            Program.Buildscript(source.fragment(1..11, 13..34))
        )
    }

    @Test
    fun `non-empty Stage 1 with non-empty Stage 2 parse to Stage 1 followed by Stage 2`() {

        val source = ProgramSource(
            "/src/fragment.gradle.kts",
            "\r\n\r\nplugins {\r\n  java\r\n}\r\nprintln(\"Stage 2\")\r\n\r\n"
        )

        assertProgramOf(
            source,
            Program.Staged(
                Program.Plugins(source.fragment(2..8, 10..19)),
                Program.Script(source.map { text("\n\n         \n      \n \nprintln(\"Stage 2\")") })
            )
        )
    }

    @Test
    fun `buildscript followed by plugins block followed by script body`() {

        val source = ProgramSource(
            "build.gradle.kts",
            """
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            print("stage 2")
            """.replaceIndent()
        )

        val expectedScript = "" +
            "                                              \n" +
            "                                      \n" +
            "print(\"stage 2\")"

        assertProgramOf(
            source,
            Program.Staged(
                Program.Stage1Sequence(
                    null,
                    Program.Buildscript(source.fragment(0..10, 12..45)),
                    Program.Plugins(source.fragment(47..53, 55..84))
                ),
                Program.Script(source.map { text(expectedScript) })
            )
        )
    }

    @Test
    fun `non-empty init script Stage 1 with empty Stage 2 parse to Stage 1`() {

        val source = programSourceWith(" initscript { println(\"Stage 1\") } ")

        assertProgramOf(
            source,
            Program.Buildscript(source.fragment(1..10, 12..33)),
            programTarget = ProgramTarget.Gradle
        )
    }

    @Test
    fun `non empty Gradle script with initscript block`() {

        val source =
            programSourceWith(" initscript { println(\"Stage 1\") }; println(\"stage 2\")")

        val scriptText =
            text("                                  ; println(\"stage 2\")")

        assertProgramOf(
            source,
            Program.Staged(
                Program.Buildscript(source.fragment(1..10, 12..33)),
                Program.Script(source.map { scriptText })
            ),
            programTarget = ProgramTarget.Gradle
        )
    }

    @Test
    fun `buildscript followed by pluginManagement block followed by plugins block followed by script body`() {

        val source = ProgramSource(
            "settings.gradle.kts",
            """
            pluginManagement { println("stage 1 pluginManagement") }
            buildscript { println("stage 1 buildscript") }
            plugins { println("stage 1 plugins") }
            print("stage 2")
            """.replaceIndent()
        )

        val expectedScript = "" +
            "                                                        \n" +
            "                                              \n" +
            "                                      \n" +
            "print(\"stage 2\")"

        assertProgramOf(
            source,
            Program.Staged(
                Program.Stage1Sequence(
                    Program.PluginManagement(source.fragment(0..15, 17..55)),
                    Program.Buildscript(source.fragment(57..67, 69..102)),
                    Program.Plugins(source.fragment(104..110, 112..141))
                ),
                Program.Script(source.map { text(expectedScript) })
            ),
            programTarget = ProgramTarget.Settings
        )
    }

    @Test
    fun `buildscript block after plugins block`() {

        val source = programSourceWith(
            """
            plugins {
                `kotlin-dsl`
            }

            buildscript {
                dependencies {
                    classpath("org.acme:plugin:1.0")
                }
            }

            dependencies {
                implementation("org.acme:lib:1.0")
            }
            """
        )

        val expectedScriptSource = programSourceWith(
            "\n                     " +
                "\n                            " +
                "\n             " +
                "\n\n                         " +
                "\n                              " +
                "\n                                                    " +
                "\n                 " +
                "\n             " +
                "\n\n            dependencies {" +
                "\n                implementation(\"org.acme:lib:1.0\")" +
                "\n            }"
        )

        assertProgramOf(
            source,
            Program.Staged(
                Program.Stage1Sequence(
                    null,
                    Program.Buildscript(source.fragment(79..89, 91..207)),
                    Program.Plugins(source.fragment(13..19, 21..64))
                ),
                Program.Script(expectedScriptSource)
            )
        )
    }

    private
    fun assertEmptyProgram(contents: String, programTarget: ProgramTarget = ProgramTarget.Project) {
        assertProgramOf(programSourceWith(contents), Program.Empty, programTarget = programTarget)
    }
}


internal
fun assertProgramOf(
    source: ProgramSource,
    expected: Program,
    programKind: ProgramKind = ProgramKind.TopLevel,
    programTarget: ProgramTarget = ProgramTarget.Project
) {
    val program = ProgramParser.parse(source, programKind, programTarget)
    val actual = program.document
    assertThat(actual, equalTo(expected))
}


internal
fun programSourceWith(contents: String) =
    ProgramSource("/src/build.gradle.kts", contents)
