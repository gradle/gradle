package model

import configurations.BuildDistributions
import configurations.ColonyCompatibility
import configurations.Gradleception
import configurations.SanityCheck
import configurations.SmokeTests
import jetbrains.buildServer.configs.kotlin.v10.BuildType

object CIBuildModel {
    val projectPrefix = "Gradle_Check_"

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
            Stage("Test Parallel, Java9, IBM VM, Cross-Version, Smoke Tests, Colony",
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

    //TODO this is a copy of `gradle/buildSplits.gradle`, we should use a shared specification
    val testBuckets = listOf(
            listOf(
                    ":platformPlay",
                    ":runtimeApiInfo"),
            listOf(
                    ":toolingApi",
                    ":compositeBuilds",
                    ":resourcesS3",
                    ":resources",
                    ":jvmServices",
                    ":docs",
                    ":distributions",
                    ":soak",
                    ":smokeTest",
                    ":buildCacheHttp",
                    ":workers"),
            listOf(
                    ":launcher",
                    ":buildComparison",
                    ":buildInit",
                    ":antlr",
                    ":signing",
                    ":testingBase",
                    ":baseServicesGroovy",
                    ":toolingApiBuilders"),
            listOf(
                    ":testKit",
                    ":languageNative",
                    ":ivy",
                    ":wrapper",
                    ":platformJvm",
                    ":baseServices",
                    ":announce",
                    ":publish"
            ),
            listOf(
                    ":integTest",
                    ":plugins",
                    ":idePlay",
                    ":platformBase",
                    ":diagnostics",
                    ":ideNative",
                    ":javascript",
                    ":languageJvm",
                    ":ear"
            ),
            listOf(
                    ":pluginUse",
                    ":dependencyManagement",
                    ":languageJava",
                    ":maven",
                    ":reporting",
                    ":languageGroovy",
                    ":internalTesting",
                    ":performance",
                    ":buildScanPerformance",
                    ":internalIntegTesting",
                    ":internalPerformanceTesting",
                    ":internalAndroidPerformanceTesting",
                    ":processServices",
                    ":installationBeacon"),
            listOf(
                    ":core",
                    ":languageScala",
                    ":scala",
                    ":platformNative",
                    ":modelCore",
                    ":resourcesSftp",
                    ":messaging",
                    ":logging",
                    ":resourcesHttp"),
            listOf(
                    ":pluginDevelopment",
                    ":codeQuality",
                    ":testingJvm",
                    ":ide",
                    ":testingNative",
                    ":jacoco",
                    ":modelGroovy",
                    ":osgi",
                    ":native",
                    ":cli")
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

enum class PerformanceTestType(val taskId: String, val timeout : Int, val defaultBaselines: String = "", val defaultBaselinesBranches: String = "", val extraParameters : String = "") {
    regression("PerformanceTest", 420, "", "--baselines nightly"),
    experiment("PerformanceExperiment", 420, "", "--baselines nightly"),
    historical("FullPerformanceTest", 2280, "--baselines 2.9,2.12,2.14.1,last", "--baselines 2.9,2.12,2.14.1,last", "--checks none");

    fun asId(): String {
        return "${CIBuildModel.projectPrefix}Performance${name.capitalize()}Coordinator"
    }
}

enum class Trigger {
    never, eachCommit, daily, weekly
}
