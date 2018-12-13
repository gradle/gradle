package org.gradle.kotlin.dsl.samples

import org.gradle.kotlin.dsl.fixtures.canPublishBuildScan
import org.junit.Test


class BuildScanSampleTest : AbstractSampleTest("build-scan") {

    @Test
    fun `publishes build scan`() {
        canPublishBuildScan()
    }
}
