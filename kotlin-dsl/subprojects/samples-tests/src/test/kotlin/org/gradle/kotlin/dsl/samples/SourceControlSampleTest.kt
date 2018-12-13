package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class SourceControlSampleTest : AbstractSampleTest("source-control") {

    @Test
    fun `source dependencies mapping`() {

        val externalDir = existing("external")
        val sampleDir = existing("sample")

        build(externalDir, "generateGitRepo")

        assertThat(
            build(sampleDir, "run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
