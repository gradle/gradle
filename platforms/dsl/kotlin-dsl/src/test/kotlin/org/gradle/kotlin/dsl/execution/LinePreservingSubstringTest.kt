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


class LinePreservingSubstringTest {

    @Test
    fun `given a range starting after the first line, it should return a substring prefixed by blank lines`() {
        val original = """
            // line 1
            // line 2
            buildscript {
                // line 4
            }
        """.replaceIndent()
        val begin = original.indexOf("buildscript")
        val end = original.indexOf("}")
        assertThat(
            original.linePreservingSubstring(begin..end),
            equalTo(
                """


                buildscript {
                    // line 4
                }""".replaceIndent()
            )
        )
    }

    @Test
    fun `given a range starting on the first line, it should return it undecorated`() {
        val original = """
            buildscript {
                // line 2
            }
        """.replaceIndent()
        val begin = original.indexOf("buildscript")
        val end = original.indexOf("}")
        assertThat(
            original.linePreservingSubstring(begin..end),
            equalTo(
                """
                buildscript {
                    // line 2
                }""".replaceIndent()
            )
        )
    }

    @Test
    fun `given ranges linePreservingBlankRange should blank lines`() {
        val original = """
            |// line 1
            |// line 2
            |buildscript {
            |    // line 4
            |}
            |// line 6
            |plugins {
            |    // line 8
            |}
            |// line 10
        """.trimMargin()
        val buildscriptRange = original.indexOf("buildscript")..original.indexOf("}")
        val pluginsRange = original.indexOf("plugins")..original.lastIndexOf("}")
        assertThat(
            original.linePreservingBlankRanges(listOf(buildscriptRange, pluginsRange)),
            equalTo(
                """
                |// line 1
                |// line 2
                |
                |
                |
                |// line 6
                |
                |
                |
                |// line 10
                """.trimMargin()
            )
        )
    }
}
