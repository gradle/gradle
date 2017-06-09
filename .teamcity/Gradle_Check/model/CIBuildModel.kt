package model

import configurations.*
import jetbrains.buildServer.configs.kotlin.v10.BuildType

object CIBuildModel {
    val projectPrefix = "Gradle_Check_"
    val testBucketCount = 8

    val stages = listOf(
            Stage("Sanity Check and Distribution",
                    specificBuilds = listOf(
                            SanityCheck,
                            BuildDistributions)),
            Stage("Test Embedded Java8 Linux",
                    functionalTests = listOf(
                            TestCoverage(TestType.quick, OS.linux, JvmVersion.java8))),
            Stage("Test Embedded Java7 Windows",
                    functionalTests = listOf(
                            TestCoverage(TestType.quick, OS.windows, JvmVersion.java7))),
            Stage("Test Forked Linux/Windows, Performance, Gradleception",
                    specificBuilds = listOf(
                            Gradleception),
                    functionalTests = listOf(
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.windows, JvmVersion.java8)),
                    performanceTests = listOf(PerformanceTestType.regression)),
            Stage("Test Parallel, Java9, IBM VM, Cross-Version, Smoke Tests, Colony, API Change Reporting",
                    trigger = Trigger.eachCommit,
                    specificBuilds = listOf(
                            SmokeTests, ColonyCompatibility),
                    functionalTests = listOf(
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.quickFeedbackCrossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.platform, OS.linux, JvmVersion.java9),
                            TestCoverage(TestType.parallel, OS.linux, JvmVersion.java7, JvmVendor.ibm))),
            Stage("Test Cross-version (All Versions), No-daemon, Soak Tests, Performance Experiments",
                    trigger = Trigger.daily,
                    functionalTests = listOf(
                            TestCoverage(TestType.soak, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.soak, OS.windows, JvmVersion.java8),
                            TestCoverage(TestType.crossVersion, OS.linux, JvmVersion.java7),
                            TestCoverage(TestType.crossVersion, OS.windows, JvmVersion.java7),
                            TestCoverage(TestType.noDaemon, OS.linux, JvmVersion.java8),
                            TestCoverage(TestType.noDaemon, OS.windows, JvmVersion.java8)),
                    performanceTests = listOf(
                            PerformanceTestType.experiment)),
            Stage("Performance Historical",
                    trigger = Trigger.weekly,
                    performanceTests = listOf(
                            PerformanceTestType.historical))
    )
}

data class Stage(val description: String, val specificBuilds: List<BuildType> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never)

data class TestCoverage(val testType: TestType, val os: OS, val version: JvmVersion, val vendor: JvmVendor = JvmVendor.oracle) {
    fun asId(): String {
        return "${CIBuildModel.projectPrefix}Test_Coverage_${testType.name.capitalize()}_${version.name.capitalize()}_${vendor.name.capitalize()}_${os.name.capitalize()}"
    }
    fun asName(): String {
        return "Test Coverage - ${testType.name.capitalize()} ${version.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}"
    }
}

enum class OS {
    linux, windows
}

enum class JvmVersion {
    java7, java8, java9,
}

enum class TestType {
    quick, platform, parallel, noDaemon, crossVersion, quickFeedbackCrossVersion, soak
}

enum class JvmVendor {
    oracle, ibm
}

enum class PerformanceTestType(val taskId: String, val defaultBaselines: String = "", val defaultBaselinesBranches: String = "", val extraParameters : String = "") {
    regression("PerformanceTest", "", "--baselines nightly"),
    experiment("PerformanceExperiment", "", "--baselines nightly"),
    historical("FullPerformanceTest", "--baselines 2.9,2.12,2.14.1,last", "", "--checks none");

    fun asId(): String {
        return "${CIBuildModel.projectPrefix}Performance${name.capitalize()}Coordinator"
    }
}

enum class Trigger {
    never, eachCommit, daily, weekly
}
