package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Ignore

@Ignore("Requires published `kotlin-dsl` plugin depending on Kotlin 1.1.60")
class MultiKotlinProjectWithBuildSrcSampleTest : AbstractSampleTest("multi-kotlin-project-with-buildSrc") {

    @org.junit.Test
    fun `can run CLI application`() {
        assertThat(
            build(":cli:run").output,
            containsString("The answer to the ultimate question of Life, the Universe and Everything is 42."))
    }
}
