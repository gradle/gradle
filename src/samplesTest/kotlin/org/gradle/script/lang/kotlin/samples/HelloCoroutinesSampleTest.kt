package org.gradle.script.lang.kotlin.samples

import org.gradle.script.lang.kotlin.fixtures.toPlatformLineSeparators
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class HelloCoroutinesSampleTest : AbstractSampleTest("hello-coroutines") {

    @Test
    fun `fibonacci`() {
        assertThat(
            build("run").output.toPlatformLineSeparators(),
            containsString("1\n1\n2\n3\n5"))
    }
}
