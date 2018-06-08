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

import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test


class BuildscriptBlockExtractionTest {

    @Test
    fun `given top-level buildscript it returns exact range`() {
        val script = """
            val foo = 42
              buildscript {
                val bar = 51
                repositories {}
                // also part of the content }}
            }dependencies {}""".replaceIndent()

        val range = extractBuildscriptBlockFrom(script)!!
        assertThat(
            script.substring(range),
            equalTo("""
                buildscript {
                    val bar = 51
                    repositories {}
                    // also part of the content }}
                }""".replaceIndent()))
    }

    @Test
    fun `given non top-level buildscript it returns null`() {
        // as we can't currently know if it's a legit call to another similarly named
        // function in a different context
        assertNoBuildscript("foo { buildscript {} }")
    }

    @Test
    fun `given top-level buildscript with typo it returns null`() {
        assertNoBuildscript("buildscripto {}")
    }

    @Test
    fun `given top-level buildscript reference it returns null`() {
        assertNoBuildscript("""
            val a = buildscript
            a.dependencies {}""")
    }

    @Test
    fun `given top-level buildscript reference followed by top-level buildscript it returns correct range`() {
        assertThat(
            extractBuildscriptBlockFrom("val a = buildscript\nbuildscript {}"),
            equalTo(20..33))
    }

    @Test
    fun `given no buildscript it returns null`() {
        assertNoBuildscript("dependencies {}")
    }

    @Test
    fun `given an empty script it returns null`() {
        assertNoBuildscript("")
    }

    @Test
    fun `given line commented buildscript it returns null`() {
        assertNoBuildscript("// no buildscript {} here")
    }

    @Test
    fun `given block commented buildscript it returns null`() {
        assertNoBuildscript("/* /* no */ buildscript {} here either */")
    }

    @Test
    fun `given more than one top level buildscript block it throws IllegalStateException`() {
        try {
            extractBuildscriptBlockFrom("buildscript {} buildscript {}")
            fail("Expecting ${UnexpectedBlock::class.simpleName}!")
        } catch (unexpectedBlock: UnexpectedBlock) {
            assertThat(unexpectedBlock.identifier, equalTo("buildscript"))
            assertThat(unexpectedBlock.location, equalTo(15..28))
            assertThat(unexpectedBlock.message, equalTo("Unexpected block found."))
        }
    }

    private
    fun assertNoBuildscript(script: String) {
        assertNull(extractBuildscriptBlockFrom(script))
    }

    private
    fun extractBuildscriptBlockFrom(script: String) =
        lex(script, "buildscript").second.singleBlockSectionOrNull()?.wholeRange
}
