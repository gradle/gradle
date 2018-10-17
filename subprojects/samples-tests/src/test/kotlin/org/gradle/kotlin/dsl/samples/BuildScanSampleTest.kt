package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.canPublishBuildScan

import org.junit.Ignore
import org.junit.Test


class BuildScanSampleTest : AbstractSampleTest("build-scan") {

    @Test
    @Ignore("This version of Gradle requires version 2.0 of the build scan plugin or later.")
    fun `publishes build scan`() {
        canPublishBuildScan()
    }
}
