package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class GroovyInteropSampleTest : AbstractSampleTest("groovy-interop") {

    @Test
    fun `stringSum`() {
        assertThat(
            build("stringSum").output,
            containsString("GroovyKotlin"))
    }

    @Test
    fun `intSum`() {
        assertThat(
            build("intSum").output,
            containsString("44"))
    }
}
