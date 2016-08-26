package org.gradle.script.lang.kotlin.codegen

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class MarkdownKDocProviderTest {

    @Test
    fun `will ignore initial header and trim each entry`() {

        val provider = MarkdownKDocProvider.from("""
Everything that comes before the first header is
ignore.

# foo.bar()

Documentation for foo.bar.

# foo.baz()

Documentation for foo.baz.""")

        assertThat(
            provider("foo.bar()"),
            equalTo(KDoc("Documentation for foo.bar.")))

        assertThat(
            provider("foo.baz()"),
            equalTo(KDoc("Documentation for foo.baz.")))
    }
}


