package org.gradle.kotlin.dsl.provider

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
            equalTo("""


                buildscript {
                    // line 4
                }""".replaceIndent())
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
            equalTo("""
                buildscript {
                    // line 2
                }""".replaceIndent())
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
            equalTo("""
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
            """.trimMargin()))
    }
}
