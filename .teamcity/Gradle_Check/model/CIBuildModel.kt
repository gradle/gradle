package model

import Gradle_Check.model.GradleSubprojectProvider
import common.BuildCache
import common.JvmCategory
import common.JvmVendor
import common.JvmVersion
import common.Os
import common.builtInRemoteBuildCacheNode
import configurations.BuildDistributions
import configurations.CompileAll
import configurations.FunctionalTest
import configurations.Gradleception
import configurations.SanityCheck
import configurations.SmokeTests
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType

enum class StageNames(override val stageName: String, override val description: String, override val uuid: String) : StageName {
    QUICK_FEEDBACK_LINUX_ONLY("Quick Feedback - Linux Only", "Run checks and functional tests (embedded executer, Linux)", "QuickFeedbackLinuxOnly"),
    QUICK_FEEDBACK("Quick Feedback", "Run checks and functional tests (embedded executer, Windows)", "QuickFeedback"),
    READY_FOR_MERGE("Ready for Merge", "Run performance and functional tests (against distribution)", "BranchBuildAccept"),
    READY_FOR_NIGHTLY("Ready for Nightly", "Rerun tests in different environments / 3rd party components", "MasterAccept"),
    READY_FOR_RELEASE("Ready for Release", "Once a day: Rerun tests in more environments", "ReleaseAccept"),
    HISTORICAL_PERFORMANCE("Historical Performance", "Once a week: Run performance tests for multiple Gradle versions", "HistoricalPerformance"),
    EXPERIMENTAL("Experimental", "On demand: Run experimental tests", "Experimental"),
    EXPERIMENTAL_VFS_RETENTION("Experimental FS Watching", "On demand checks to run tests with file system watching enabled", "ExperimentalVfsRetention"),
    EXPERIMENTAL_JDK15("Experimental JDK15", "On demand checks to run tests with JDK15", "ExperimentalJDK15"),
    EXPERIMENTAL_JDK16("Experimental JDK16", "On demand checks to run tests with JDK16", "ExperimentalJDK16"),
}

data class CIBuildModel(
    val projectPrefix: String = "Gradle_Check_",
    val rootProjectName: String = "Check",
    val publishStatusToGitHub: Boolean = true,
    val parentBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val childBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val buildScanTags: List<String> = emptyList(),
    val stages: List<Stage> = listOf(
        Stage(StageNames.QUICK_FEEDBACK_LINUX_ONLY,
            specificBuilds = listOf(
                SpecificBuild.CompileAll, SpecificBuild.SanityCheck),
            functionalTests = listOf(
                TestCoverage(1, TestType.quick, Os.linux, JvmCategory.MAX_VERSION)), omitsSlowProjects = true),
        Stage(StageNames.QUICK_FEEDBACK,
            functionalTests = listOf(
                TestCoverage(2, TestType.quick, Os.windows, JvmCategory.MIN_VERSION)),
            functionalTestsDependOnSpecificBuilds = true,
            omitsSlowProjects = true,
            dependsOnSanityCheck = true),
        Stage(StageNames.READY_FOR_MERGE,
            specificBuilds = listOf(
                SpecificBuild.BuildDistributions,
                SpecificBuild.Gradleception,
                SpecificBuild.SmokeTestsMaxJavaVersion,
                SpecificBuild.ConfigCacheSmokeTestsMaxJavaVersion,
                SpecificBuild.ConfigCacheSmokeTestsMinJavaVersion
            ),
            functionalTests = listOf(
                TestCoverage(3, TestType.platform, Os.linux, JvmCategory.MIN_VERSION),
                TestCoverage(4, TestType.platform, Os.windows, JvmCategory.MAX_VERSION),
                TestCoverage(20, TestType.configCache, Os.linux, JvmCategory.MIN_VERSION)),
            performanceTests = listOf(PerformanceTestType.test),
            omitsSlowProjects = true),
        Stage(StageNames.READY_FOR_NIGHTLY,
            trigger = Trigger.eachCommit,
            specificBuilds = listOf(
                SpecificBuild.SmokeTestsMinJavaVersion
            ),
            functionalTests = listOf(
                TestCoverage(5, TestType.quickFeedbackCrossVersion, Os.linux, JvmCategory.MIN_VERSION),
                TestCoverage(6, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.MIN_VERSION),
                TestCoverage(28, TestType.watchFs, Os.linux, JvmCategory.MAX_VERSION))
        ),
        Stage(StageNames.READY_FOR_RELEASE,
            trigger = Trigger.daily,
            functionalTests = listOf(
                TestCoverage(7, TestType.parallel, Os.linux, JvmCategory.MAX_VERSION),
                TestCoverage(8, TestType.soak, Os.linux, JvmCategory.MAX_VERSION),
                TestCoverage(9, TestType.soak, Os.windows, JvmCategory.MIN_VERSION),
                TestCoverage(35, TestType.soak, Os.macos, JvmCategory.MIN_VERSION),
                TestCoverage(10, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.MIN_VERSION),
                TestCoverage(11, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.MIN_VERSION),
                TestCoverage(12, TestType.noDaemon, Os.linux, JvmCategory.MIN_VERSION),
                TestCoverage(13, TestType.noDaemon, Os.windows, JvmCategory.MAX_VERSION),
                TestCoverage(14, TestType.platform, Os.macos, JvmCategory.MIN_VERSION, expectedBucketNumber = 20),
                TestCoverage(15, TestType.forceRealizeDependencyManagement, Os.linux, JvmCategory.MIN_VERSION),
                TestCoverage(33, TestType.allVersionsIntegMultiVersion, Os.linux, JvmCategory.MIN_VERSION, expectedBucketNumber = 10),
                TestCoverage(34, TestType.allVersionsIntegMultiVersion, Os.windows, JvmCategory.MIN_VERSION, expectedBucketNumber = 10),
                // Only Java 8 VFS retention tests pass on macOS, since later versions have problems
                // with the JDK watcher and continuous build.
                TestCoverage(31, TestType.watchFs, Os.macos, JvmCategory.MIN_VERSION),
                TestCoverage(30, TestType.watchFs, Os.windows, JvmCategory.MAX_VERSION)),
            performanceTests = listOf(
                PerformanceTestType.slow)),
        Stage(StageNames.HISTORICAL_PERFORMANCE,
            trigger = Trigger.weekly,
            performanceTests = listOf(
                PerformanceTestType.historical, PerformanceTestType.flakinessDetection, PerformanceTestType.experiment)),
        Stage(StageNames.EXPERIMENTAL,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(16, TestType.quick, Os.linux, JvmCategory.EXPERIMENTAL_VERSION),
                TestCoverage(17, TestType.quick, Os.windows, JvmCategory.EXPERIMENTAL_VERSION),
                TestCoverage(18, TestType.platform, Os.linux, JvmCategory.EXPERIMENTAL_VERSION),
                TestCoverage(19, TestType.platform, Os.windows, JvmCategory.EXPERIMENTAL_VERSION),
                TestCoverage(21, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.MAX_VERSION))),
        Stage(StageNames.EXPERIMENTAL_VFS_RETENTION,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(27, TestType.watchFs, Os.linux, JvmCategory.MIN_VERSION, withoutDependencies = true),
                TestCoverage(36, TestType.watchFs, Os.linux, JvmCategory.MAX_VERSION, withoutDependencies = true),
                TestCoverage(29, TestType.watchFs, Os.windows, JvmCategory.MIN_VERSION, withoutDependencies = true),
                TestCoverage(38, TestType.watchFs, Os.windows, JvmCategory.MAX_VERSION, withoutDependencies = true),
                TestCoverage(32, TestType.watchFs, Os.macos, JvmCategory.MAX_VERSION, withoutDependencies = true),
                TestCoverage(37, TestType.watchFs, Os.macos, JvmCategory.MIN_VERSION, withoutDependencies = true)
            )),
        Stage(StageNames.EXPERIMENTAL_JDK15,
            trigger = Trigger.never,
            runsIndependent = true,
            specificBuilds = listOf(
                SpecificBuild.SmokeTestsJDK15,
                SpecificBuild.ConfigCacheSmokeTestsJDK15
            ),
            functionalTests = listOf(
                TestCoverage(40, TestType.quick, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(41, TestType.quick, Os.windows, JvmCategory.OPENJDK15),
                TestCoverage(42, TestType.platform, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(43, TestType.platform, Os.windows, JvmCategory.OPENJDK15),
                TestCoverage(44, TestType.configCache, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(45, TestType.quickFeedbackCrossVersion, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(46, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.OPENJDK15),
                TestCoverage(47, TestType.parallel, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(48, TestType.soak, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(49, TestType.soak, Os.windows, JvmCategory.OPENJDK15),
                TestCoverage(50, TestType.soak, Os.macos, JvmCategory.OPENJDK15),
                TestCoverage(51, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(52, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.OPENJDK15),
                TestCoverage(53, TestType.noDaemon, Os.linux, JvmCategory.OPENJDK15),
                TestCoverage(54, TestType.noDaemon, Os.windows, JvmCategory.OPENJDK15)
            )),
        Stage(StageNames.EXPERIMENTAL_JDK16,
            trigger = Trigger.never,
            runsIndependent = true,
            specificBuilds = listOf(
                SpecificBuild.SmokeTestsJDK16,
                SpecificBuild.ConfigCacheSmokeTestsJDK16
            ),
            functionalTests = listOf(
                TestCoverage(55, TestType.quick, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(56, TestType.quick, Os.windows, JvmCategory.OPENJDK16),
                TestCoverage(57, TestType.platform, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(58, TestType.platform, Os.windows, JvmCategory.OPENJDK16),
                TestCoverage(59, TestType.configCache, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(60, TestType.quickFeedbackCrossVersion, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(61, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.OPENJDK16),
                TestCoverage(62, TestType.parallel, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(63, TestType.soak, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(64, TestType.soak, Os.windows, JvmCategory.OPENJDK16),
                TestCoverage(65, TestType.soak, Os.macos, JvmCategory.OPENJDK16),
                TestCoverage(66, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(67, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.OPENJDK16),
                TestCoverage(68, TestType.noDaemon, Os.linux, JvmCategory.OPENJDK16),
                TestCoverage(69, TestType.noDaemon, Os.windows, JvmCategory.OPENJDK16)
            ))
    ),
    val subprojects: GradleSubprojectProvider
)

interface BuildTypeBucket {
    fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int): FunctionalTest

    fun getUuid(model: CIBuildModel, testCoverage: TestCoverage, bucketIndex: Int): String = testCoverage.asConfigurationId(model, "bucket${bucketIndex + 1}")

    fun getName(testCoverage: TestCoverage): String = throw UnsupportedOperationException()

    fun getDescription(testCoverage: TestCoverage): String = throw UnsupportedOperationException()
}

data class GradleSubproject(val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val containsSlowTests: Boolean = false) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, bucketIndex: Int): FunctionalTest {
        val uuid = if (containsSlowTests) testCoverage.asConfigurationId(model, name.kebabCaseToCamelCase()) else getUuid(model, testCoverage, bucketIndex)
        return FunctionalTest(model,
            uuid,
            getName(testCoverage),
            getDescription(testCoverage),

            testCoverage,
            stage,
            listOf(name)
        )
    }

    // Build Template or Configuration "Gradle_Check_Platform_4_platform-play" is invalid: contains unsupported character '-'. ID should start with a latin letter
    // and contain only latin letters, digits and underscores (at most 225 characters).
    private fun String.kebabCaseToCamelCase() = split('-')
        .map { it.capitalize() }
        .joinToString("")
        .decapitalize()

    override fun getName(testCoverage: TestCoverage): String = "${testCoverage.asName()} ($name)"

    override fun getDescription(testCoverage: TestCoverage) = "${testCoverage.asName()} for $name"

    fun hasTestsOf(testType: TestType) = (unitTests && testType.unitTests) || (functionalTests && testType.functionalTests) || (crossVersionTests && testType.crossVersionTests)

    fun asDirectoryName() = name.replace(Regex("([A-Z])")) { "-" + it.groups[1]!!.value.toLowerCase() }
}

interface StageName {
    val stageName: String
    val description: String
    val uuid: String
        get() = id
    val id: String
        get() = stageName.replace(" ", "").replace("-", "")
}

data class Stage(val stageName: StageName, val specificBuilds: List<SpecificBuild> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never, val functionalTestsDependOnSpecificBuilds: Boolean = false, val runsIndependent: Boolean = false, val omitsSlowProjects: Boolean = false, val dependsOnSanityCheck: Boolean = false) {
    val id = stageName.id
}

data class TestCoverage(val uuid: Int, val testType: TestType, val os: Os, val testJvmVersion: JvmVersion, val vendor: JvmVendor = JvmVendor.oracle, val buildJvmVersion: JvmVersion = JvmVersion.java11, val expectedBucketNumber: Int = 50, val withoutDependencies: Boolean = false, val testDistribution: Boolean = false) {

    constructor(uuid: Int, testType: TestType, os: Os, testJvm: JvmCategory, buildJvmVersion: JvmVersion = JvmVersion.java11, expectedBucketNumber: Int = 50, withoutDependencies: Boolean = false, testDistribution: Boolean = false) :
        this(uuid, testType, os, testJvm.version, testJvm.vendor, buildJvmVersion, expectedBucketNumber, withoutDependencies, testDistribution)

    fun asId(model: CIBuildModel): String {
        return "${model.projectPrefix}$testCoveragePrefix"
    }

    private
    val testCoveragePrefix
        get() = "${testType.name.capitalize()}_$uuid"

    fun asConfigurationId(model: CIBuildModel, subProject: String = ""): String {
        val prefix = "${testCoveragePrefix}_"
        val shortenedSubprojectName = shortenSubprojectName(model.projectPrefix, prefix + subProject)
        return model.projectPrefix + if (subProject.isNotEmpty()) shortenedSubprojectName else "${prefix}0"
    }

    private
    fun shortenSubprojectName(prefix: String, subProjectName: String): String {
        val shortenedSubprojectName = subProjectName.replace("internal", "i").replace("Testing", "T")
        if (shortenedSubprojectName.length + prefix.length <= 80) {
            return shortenedSubprojectName
        }
        return shortenedSubprojectName.replace(Regex("[aeiou]"), "")
    }

    fun asName(): String =
        "Test Coverage - ${testType.name.capitalize()} ${testJvmVersion.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}${if (withoutDependencies) " without dependencies" else ""}"

    val isQuick: Boolean = withoutDependencies || testType == TestType.quick
}

enum class TestType(val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val timeout: Int = 180) {
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    quick(true, true, true, 60),

    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    platform(true, true, true),

    // Cross version tests select a small set of versions to cover when run as part of this stage
    quickFeedbackCrossVersion(false, false, true),

    // Cross version tests select all versions to cover when run as part of this stage
    allVersionsCrossVersion(false, false, true, 240),

    // run integMultiVersionTest with all version to cover
    allVersionsIntegMultiVersion(false, true, false),
    parallel(false, true, false),
    noDaemon(false, true, false, 240),
    configCache(false, true, false),
    watchFs(false, true, false),
    soak(false, false, false),
    forceRealizeDependencyManagement(false, true, false)
}

enum class PerformanceTestType(val taskId: String, val displayName: String, val timeout: Int, val defaultBaselines: String = "", val extraParameters: String = "", val uuid: String? = null) {
    test("PerformanceTest", "Performance Regression Test", 420, "defaults"),
    slow("SlowPerformanceTest", "Slow Performance Regression Test", 420, "defaults", uuid = "PerformanceExperimentCoordinator"),
    experiment("PerformanceExperiment", "Performance Experiment", 420, "defaults", uuid = "PerformanceExperimentOnlyCoordinator"),
    flakinessDetection("FlakinessDetection", "Performance Test Flakiness Detection", 600, "flakiness-detection-commit"),
    historical("HistoricalPerformanceTest", "Historical Performance Test", 2280, "3.5.1,4.10.3,5.6.4,last", "--checks none");

    fun asId(model: CIBuildModel): String =
        "${model.projectPrefix}Performance${name.capitalize()}Coordinator"

    fun asUuid(model: CIBuildModel): String =
        uuid?.let { model.projectPrefix + it } ?: asId(model)
}

enum class Trigger {
    never, eachCommit, daily, weekly
}

enum class SpecificBuild {
    CompileAll {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return CompileAll(model, stage)
        }
    },
    SanityCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SanityCheck(model, stage)
        }
    },
    BuildDistributions {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return BuildDistributions(model, stage)
        }
    },
    Gradleception {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return Gradleception(model, stage)
        }
    },
    SmokeTestsMinJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MIN_VERSION)
        }
    },
    SmokeTestsMaxJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MAX_VERSION)
        }
    },
    ConfigCacheSmokeTestsMinJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MIN_VERSION, "configCacheSmokeTest")
        }
    },
    ConfigCacheSmokeTestsMaxJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MAX_VERSION, "configCacheSmokeTest")
        }
    },
    SmokeTestsJDK15 {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.OPENJDK15)
        }
    },
    ConfigCacheSmokeTestsJDK15 {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.OPENJDK15, "configCacheSmokeTest")
        }
    },
    SmokeTestsJDK16 {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.OPENJDK16)
        }
    },
    ConfigCacheSmokeTestsJDK16 {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.OPENJDK16, "configCacheSmokeTest")
        }
    };

    abstract fun create(model: CIBuildModel, stage: Stage): BuildType
}
