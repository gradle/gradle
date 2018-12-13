package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class ExtraPropertiesSampleTest : AbstractSampleTest("extra-properties") {

    @Test
    fun `extra properties`() {
        assertThat(
            build("test").output,
            allOf(
                containsString("myTask.foo = 42"),
                containsString("Extra foo property value: 42"),
                containsString("myTask.bar = null"),
                containsString("Optional extra bar property value: null")))
    }
}
