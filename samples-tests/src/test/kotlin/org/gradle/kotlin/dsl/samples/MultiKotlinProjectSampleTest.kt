package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class MultiKotlinProjectSampleTest : AbstractSampleTest("multi-kotlin-project") {

    @Test
    fun `can run CLI application`() {
        assertThat(
            build(":cli:run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
