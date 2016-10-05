package org.gradle.script.lang.kotlin.codegen

import org.gradle.util.TextUtil.normaliseLineSeparators

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class KDocTest {

    @Test
    fun `inserts notice before first @param tag`() {
        assertThat(
            KDoc("Lorem ipsum.\n@param foo")
                .format("A notice.")
                .let(::normaliseLineSeparators),
            equalTo("""
                /**
                 * Lorem ipsum.
                 * A notice.
                 *
                 * @param foo
                 */
            """.replaceIndent() + '\n'))
    }

    @Test
    fun `can extract parameter names`() {
        assertThat(
            KDoc("@param one\n@param two some doc.\n@param three").parameterNames,
            equalTo(listOf("one", "two", "three")))
    }
}
