package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.junit.Assert.assertThat
import org.junit.Test


class HelloCoroutinesSampleTest : AbstractSampleTest("hello-coroutines") {

    @Test
    fun `fibonacci`() {
        assertThat(
            build("run").output,
            containsMultiLineString("""
                1
                1
                2
                3
                5
            """))
    }
}
