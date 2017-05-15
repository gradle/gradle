package org.gradle.script.lang.kotlin.samples

import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import org.junit.Ignore
import org.junit.Test

@Ignore("Current Gradle snapshot is incompatible with build-scan 1.7.1")
class BuildScanSampleTest : AbstractSampleTest("build-scan") {

    @Test
    fun `publishes build scan`() {
        assertThat(
            build("tasks", "--scan").output,
            containsString("Publishing build information..."))
    }
}
