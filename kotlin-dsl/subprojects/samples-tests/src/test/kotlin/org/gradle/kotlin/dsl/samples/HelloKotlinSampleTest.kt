package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class HelloKotlinSampleTest : AbstractSampleTest("hello-kotlin") {

    @Test
    fun `hello kotlin world`() {
        assertThat(
            build("run").output,
            containsString("Hello, world!"))
    }
}
