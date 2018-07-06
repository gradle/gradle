package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class CompositeBuildsSampleTest : AbstractSampleTest("composite-builds") {

    @Test
    fun `run cli`() {
        assertThat(
            build(":run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
