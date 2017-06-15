package org.gradle.script.lang.kotlin.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Test

class BuildScanSampleTest : AbstractSampleTest("build-scan") {

    @Test
    fun `publishes build scan`() {
        assertThat(
            build("tasks", "--scan").output,
            containsString("Publishing build scan..."))
    }
}
