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

import org.gradle.kotlin.dsl.execution.TopLevelBlockId.buildscript
import org.gradle.kotlin.dsl.execution.TopLevelBlockId.plugins

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class LexerTest {

    @Test
    fun `extracts comments, annotations and top-level blocks`() {

        assertThat(
            lex(
                "/* ... @TaskAction ... */" +
                    "\n// ... @Suppress(\"unused_variable\")" +
                    "\nbuildscript { /*" +
                    "\n ... */" +
                    "\n}" +
                    "\n@Suppress(\"unused_variable\")" +
                    "\nplugins { /*" +
                    "\n ... */" +
                    "\n}" +
                    "\n// ..." +
                    "\n   @TaskAction" +
                    "\nprintln(\"Yolo!\")",
                buildscript, plugins
            ),
            equalTo(
                Packaged(
                    null,
                    LexedScript(
                        listOf(
                            0..24,
                            26..60,
                            76..85,
                            128..137,
                            141..146
                        ),
                        listOf(
                            89..116,
                            151..161
                        ),
                        listOf(
                            topLevelBlock(buildscript, 62..72, 74..87, null),
                            topLevelBlock(plugins, 118..124, 126..139, 89)
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `extracts package name`() {
        assertThat(
            lex("\n" +
                "/* ... */\n" +
                "// she-bangs! ///////_*&@ because why not! _-|\n" +
                "\n" +
                "#!/something/something\n" +
                "\n" +
                "\n" +
                "/* first file annotation */\n" +
                "@file:Suppress(\"UnstableApiUsage\")\n" +
                "\n" +
                "// second file annotation //second comment, just for fun\n" +
                "\n" +
                "@file    :    [   SuppressWarnings    Incubating   Suppress(\n" +
                "       \"unused\",\n" +
                "       \"nothing_to_inline\"\n" +
                ")    ]\n" +
                "\n" +
                "/* /* one more weird comment here */ */" +
                "package com.example\n" +
                "plugins { }\n", plugins),
            equalTo(
                Packaged(
                    "com.example",
                    LexedScript(
                        listOf(1..9, 11..56, 84..110, 148..203, 319..357),
                        listOf(),
                        listOf(topLevelBlock(plugins, 378..384, 386..388, null))
                    )
                )
            )
        )
    }
}
