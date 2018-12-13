package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class ModularitySampleTest : AbstractSampleTest("modularity") {

    @Test
    fun `modularity`() {
        assertThat(
            build("foo", "bar", "lorem").output,
            allOf(
                containsString("Foo!"),
                containsString("Bar!")))
    }
}
