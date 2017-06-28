package org.gradle.kotlin.dsl.samples

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertThat
import org.junit.Test

class BuildScanSampleTest : AbstractSampleTest("build-scan") {

    @Test
    fun `publishes build scan`() {
        assertThat(
            build("tasks", "--scan").output,
            allOf(
                containsString("Publishing build scan..."),
                not(containsString("The build scan plugin was applied after other plugins."))))
    }
}
