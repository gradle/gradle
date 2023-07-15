/*
 * Copyright 2023 the original author or authors.
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

import org.junit.Test


class ProgramParserAnnotationErasureTest {

    @Test
    fun `empty Stage 1 with non-empty Stage 2 parse to Stage 2 with Stage 1 fragments erased`() {
        val scriptOnlySource =
            programSourceWith("""
                @Suppress("unused_variable")
                println("Stage 2")""".trimIndent())

        assertProgramOf(
            scriptOnlySource,
            Program.Script(scriptOnlySource)
        )

        val emptyBuildscriptSource =
            programSourceWith("""
                buildscript { }
                @Suppress("unused_variable")
                println("Stage 2")""".trimIndent())

        assertProgramOf(
            emptyBuildscriptSource,
            Program.Script(
                emptyBuildscriptSource.map {
                    text("               \n@Suppress(\"unused_variable\")\nprintln(\"Stage 2\")")
                }
            )
        )
    }

    @Test
    fun `non-empty Stage 1 with empty Stage 2 parse to Stage 1`() {
        val source = programSourceWith("""
            @Suppress("unused_variable")
            buildscript { println("Stage 1") }
            """.trimIndent())

        assertProgramOf(
            source,
            Program.Buildscript(source.fragment(29..39, 41..62, 0))
        )
    }

    @Test
    fun `non-empty Stage 1 with non-empty Stage 2 parse to Stage 1 followed by Stage 2`() {
        val source = ProgramSource(
            "/src/fragment.gradle.kts",
            """
            // a comment
            @Suppress("unused_variable")

            /* more comments */
            @Suppress("whatever")

            plugins {
              java
            }
            @Suppress("nothing_really")
            println("Stage 2")
            """.trimIndent()
        )

        val expectedScript = "            \n" +
            "                            \n" +
            "\n                   " +
            "\n                     " +
            "\n" +
            "\n         " +
            "\n      " +
            "\n " +
            "\n" +
            "@Suppress(\"nothing_really\")\n" +
            "println(\"Stage 2\")"

        assertProgramOf(
            source,
            Program.Staged(
                Program.Plugins(source.fragment(86..92, 94..103, 13)),
                Program.Script(source.map { text(expectedScript) })
            )
        )
    }

    @Test
    fun `buildscript block after plugins block`() {
        val source = programSourceWith(
            """
            @Suppress("unused_variable")
            plugins {
                `kotlin-dsl`
            }

            @Suppress("whatever")
            buildscript {
                dependencies {
                    classpath("org.acme:plugin:1.0")
                }
            }

            @Suppress("nothing_at_all")
            dependencies {
                implementation("org.acme:lib:1.0")
            }
            """.trimIndent()
        )

        val expectedScriptSource = programSourceWith(
            "                            \n" +
                "         \n" +
                "                \n" +
                " \n" +
                "\n" +
                "                     \n" +
                "             \n" +
                "                  \n" +
                "                                        \n" +
                "     \n" +
                " \n" +
                "\n" +
                "@Suppress(\"nothing_at_all\")\n" +
                "dependencies {\n" +
                "    implementation(\"org.acme:lib:1.0\")\n" +
                "}"
        )

        assertProgramOf(
            source,
            Program.Staged(
                Program.Stage1Sequence(
                    null,
                    Program.Buildscript(source.fragment(81..91, 93..161)),
                    Program.Plugins(source.fragment(29..35, 37..56))
                ),
                Program.Script(expectedScriptSource)
            )
        )
    }
}
