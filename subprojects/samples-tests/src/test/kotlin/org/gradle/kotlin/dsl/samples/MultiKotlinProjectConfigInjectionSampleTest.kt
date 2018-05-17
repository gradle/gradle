package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test


class MultiKotlinProjectConfigInjectionSampleTest : AbstractSampleTest("multi-kotlin-project-config-injection") {

    @Test
    fun `can run CLI application`() {
        assertThat(
            build(":cli:run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
