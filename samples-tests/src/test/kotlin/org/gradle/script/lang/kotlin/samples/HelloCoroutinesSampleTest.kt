package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.containsMultiLineString

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
