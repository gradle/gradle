package org.gradle.script.lang.kotlin.provider

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import kotlin.test.assertNull

class BuildScriptExtractionTest {

    @Test
    fun `given top-level buildscript it returns exact range`() {
        val script = """
            val foo = 42
              buildscript {
                val bar = 51
                repositories {}
                // also part of the content }}
            }dependencies {}""".replaceIndent()

        val range = extractBuildScriptFrom(script)!!
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
        assertNull(
            extractBuildScriptFrom("foo { buildscript {} }"))
    }

    @Test
    fun `given top-level buildscript with typo it returns null`() {
        assertNull(
            extractBuildScriptFrom("buildscripto {}"))
    }

    @Test
    fun `given top-level buildscript reference it returns null`() {
        assertNull(
            extractBuildScriptFrom("""
                val a = buildscript
                a.dependencies {}"""))
    }

    @Test
    fun `given top-level buildscript reference followed by top-level buildscript it returns correct range`() {
        assertThat(
            extractBuildScriptFrom("val a = buildscript\nbuildscript {}"),
            equalTo(20..33))
    }

    @Test
    fun `given no buildscript it returns null`() {
        assertNull(
            extractBuildScriptFrom("dependencies {}"))
    }

    @Test
    fun `given an empty script it returns null`() {
        assertNull(
            extractBuildScriptFrom(""))
    }

    @Test
    fun `given line commented buildscript it returns null`() {
        assertNull(
            extractBuildScriptFrom("// no buildscript {} here"))
    }

    @Test
    fun `given block commented buildscript it returns null`() {
        assertNull(
            extractBuildScriptFrom("/* /* no */ buildscript {} here either */"))
    }
}

