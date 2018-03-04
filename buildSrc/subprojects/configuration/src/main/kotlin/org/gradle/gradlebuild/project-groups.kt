package org.gradle.gradlebuild

object ProjectGroups {
    val excludedFromVulnerabilityCheck = setOf(
        ":buildScanPerformance",
        ":distributions",
        ":docs",
        ":integTest",
        ":internalAndroidPerformanceTesting",
        ":internalIntegTesting",
        ":internalPerformanceTesting",
        ":internalTesting",
        ":performance",
        ":runtimeApiInfo",
        ":smokeTest",
        ":soak")
}
