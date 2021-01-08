package org.gradle.kotlin.dsl.resolver

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class CompactTreeTest {

    @Test
    fun `it will compact common prefixes`() {
        assertThat(
            compactStringFor(listOf("/a/b/c", "/a/b/d", "/a/e/c"), separator = '/'),
            equalTo("/a/{b/{c, d}, e/c}")
        )
    }
}
