package org.gradle.kotlin.dsl.samples

import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test


class BuildCacheSampleTest : AbstractSampleTest("build-cache") {

    @Test
    fun `compileJava tasks gets cached`() {

        build("build")

        assertThat(
            build("clean", "build").outcomeOf(":compileJava"),
            equalTo(TaskOutcome.FROM_CACHE))
    }
}
