package model

import common.Arch
import common.BuildToolBuildJvm
import common.Jvm
import common.JvmCategory
import common.JvmVendor
import common.JvmVersion
import common.Os
import common.VersionedSettingsBranch
import common.toCamelCase
import common.toCapitalized
import configurations.BuildDistributions
import configurations.BuildLogicTest
import configurations.CheckLinks
import configurations.CheckTeamCityKotlinDSL
import configurations.CompileAll
import configurations.DocsTestType
import configurations.DocsTestType.CONFIG_CACHE_DISABLED
import configurations.DocsTestType.CONFIG_CACHE_ENABLED
import configurations.FunctionalTest
import configurations.Gradleception
import configurations.OsAwareBaseGradleBuildType
import configurations.SanityCheck
import configurations.SmokeIdeTests
import configurations.SmokeTests
import configurations.TestPerformanceTest
import projects.DEFAULT_FUNCTIONAL_TEST_BUCKET_SIZE
import projects.DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE
import projects.DEFAULT_MACOS_FUNCTIONAL_TEST_BUCKET_SIZE

enum class StageName(
    val stageName: String,
    val description: String,
    val uuid: String,
) {
    QUICK_FEEDBACK_LINUX_ONLY(
        "Quick Feedback - Linux Only",
        "Run checks and functional tests (embedded executer, Linux)",
        "QuickFeedbackLinuxOnly",
    ),
    QUICK_FEEDBACK("Quick Feedback", "Run checks and functional tests (embedded executer, Windows)", "QuickFeedback"),
    PULL_REQUEST_FEEDBACK("Pull Request Feedback", "Run various functional tests", "PullRequestFeedback"),
    READY_FOR_NIGHTLY(
        "Ready for Nightly",
        "Rerun tests in different environments / 3rd party components",
        "ReadyforNightly",
    ),
    READY_FOR_RELEASE("Ready for Release", "Once a day: Rerun tests in more environments", "ReadyforRelease"),
    WEEKLY_VALIDATION(
        "Weekly Validation",
        "Once a week: Run tests in even more environments but less often",
        "WeeklyValidation",
    ),
    HISTORICAL_PERFORMANCE(
        "Historical Performance",
        "Once a week: Run performance tests for multiple Gradle versions",
        "HistoricalPerformance",
    ),
    ;

    val id: String
        get() = stageName.replace(" ", "").replace("-", "")
}

private val performanceRegressionTestCoverages =
    listOf(
        PerformanceTestCoverage(
            1,
            PerformanceTestType.PER_COMMIT,
            Os.LINUX,
            numberOfBuckets = 50,
            oldUuid = "PerformanceTestTestLinux",
        ),
        PerformanceTestCoverage(
            6,
            PerformanceTestType.PER_COMMIT,
            Os.WINDOWS,
            numberOfBuckets = 10,
            failsStage = true,
        ),
        PerformanceTestCoverage(
            7,
            PerformanceTestType.PER_COMMIT,
            Os.MACOS,
            Arch.AARCH64,
            numberOfBuckets = 5,
            failsStage = true,
        ),
    )

private val slowPerformanceTestCoverages =
    listOf(
        PerformanceTestCoverage(
            2,
            PerformanceTestType.PER_DAY,
            Os.LINUX,
            numberOfBuckets = 30,
            oldUuid = "PerformanceTestSlowLinux",
        ),
    )

data class CIBuildModel(
    val branch: VersionedSettingsBranch,
    val projectId: String,
    val publishStatusToGitHub: Boolean = true,
    val buildScanTags: List<String> = emptyList(),
    val stages: List<Stage> =
        listOf(
            Stage(
                StageName.QUICK_FEEDBACK_LINUX_ONLY,
                specificBuilds =
                    listOf(
                        SpecificBuild.CompileAll,
                        SpecificBuild.SanityCheck,
                        SpecificBuild.BuildLogicTest,
                    ),
                functionalTests =
                    listOf(
                        TestCoverage(
                            1,
                            TestType.QUICK,
                            Os.LINUX,
                            JvmCategory.MAX_VERSION,
                            expectedBucketNumber = DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                    ),
            ),
            Stage(
                StageName.QUICK_FEEDBACK,
                functionalTests =
                    listOf(
                        TestCoverage(2, TestType.QUICK, Os.WINDOWS, JvmCategory.MIN_VERSION),
                    ),
            ),
            Stage(
                StageName.PULL_REQUEST_FEEDBACK,
                specificBuilds =
                    listOf(
                        SpecificBuild.BuildDistributions,
                        SpecificBuild.Gradleception,
                        SpecificBuild.CheckLinks,
                        SpecificBuild.CheckTeamCityKotlinDSL,
                        SpecificBuild.SmokeTestsMaxJavaVersion,
                        SpecificBuild.ConfigCacheSantaTrackerSmokeTests,
                        SpecificBuild.GradleBuildSmokeTests,
                        SpecificBuild.ConfigCacheSmokeTestsMaxJavaVersion,
                        SpecificBuild.ConfigCacheSmokeTestsMinJavaVersion,
                        SpecificBuild.SmokeIdeTests,
                    ),
                functionalTests =
                    listOf(
                        TestCoverage(
                            3,
                            TestType.PLATFORM,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                        TestCoverage(4, TestType.PLATFORM, Os.WINDOWS, JvmCategory.MAX_VERSION),
                        TestCoverage(
                            20,
                            TestType.CONFIG_CACHE,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                        TestCoverage(
                            21,
                            TestType.ISOLATED_PROJECTS,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                    ),
                docsTests =
                    listOf(
                        DocsTestCoverage(
                            Os.LINUX,
                            JvmCategory.MAX_VERSION,
                            listOf(CONFIG_CACHE_ENABLED, CONFIG_CACHE_DISABLED),
                        ),
                        DocsTestCoverage(Os.WINDOWS, JvmCategory.MAX_VERSION, listOf(CONFIG_CACHE_DISABLED)),
                    ),
            ),
            Stage(
                StageName.READY_FOR_NIGHTLY,
                trigger = Trigger.EACH_COMMIT,
                specificBuilds =
                    listOf(
                        SpecificBuild.SmokeTestsMinJavaVersion,
                    ),
                functionalTests =
                    listOf(
                        TestCoverage(
                            5,
                            TestType.QUICK_FEEDBACK_CROSS_VERSION,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            QUICK_CROSS_VERSION_BUCKETS.size,
                        ),
                        TestCoverage(
                            6,
                            TestType.QUICK_FEEDBACK_CROSS_VERSION,
                            Os.WINDOWS,
                            JvmCategory.MIN_VERSION,
                            QUICK_CROSS_VERSION_BUCKETS.size,
                        ),
                    ),
                performanceTests = performanceRegressionTestCoverages,
            ),
            Stage(
                StageName.READY_FOR_RELEASE,
                trigger = Trigger.DAILY,
                specificBuilds =
                    listOf(
                        SpecificBuild.TestPerformanceTest,
                        SpecificBuild.SantaTrackerSmokeTests,
                    ),
                functionalTests =
                    listOf(
                        TestCoverage(
                            7,
                            TestType.PARALLEL,
                            Os.LINUX,
                            JvmCategory.MAX_LTS_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                        TestCoverage(8, TestType.SOAK, Os.LINUX, JvmCategory.MAX_LTS_VERSION, 1),
                        TestCoverage(9, TestType.SOAK, Os.WINDOWS, JvmCategory.MIN_VERSION, 1),
                        TestCoverage(35, TestType.SOAK, Os.MACOS, JvmCategory.MAX_LTS_VERSION, 1, arch = Arch.AARCH64),
                        TestCoverage(
                            10,
                            TestType.ALL_VERSIONS_CROSS_VERSION,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            ALL_CROSS_VERSION_BUCKETS.size,
                        ),
                        TestCoverage(
                            11,
                            TestType.ALL_VERSIONS_CROSS_VERSION,
                            Os.WINDOWS,
                            JvmCategory.MIN_VERSION,
                            ALL_CROSS_VERSION_BUCKETS.size,
                        ),
                        TestCoverage(
                            12,
                            TestType.NO_DAEMON,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                        TestCoverage(13, TestType.NO_DAEMON, Os.WINDOWS, JvmCategory.MAX_LTS_VERSION),
                        TestCoverage(
                            14,
                            TestType.PLATFORM,
                            Os.MACOS,
                            JvmCategory.MIN_VERSION,
                            expectedBucketNumber = 5,
                            arch = Arch.AMD64,
                        ),
                        TestCoverage(
                            15,
                            TestType.FORCE_REALIZE_DEPENDENCY_MANAGEMENT,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                        TestCoverage(
                            33,
                            TestType.ALL_VERSIONS_INTEG_MULTI_VERSION,
                            Os.LINUX,
                            JvmCategory.MIN_VERSION,
                            ALL_CROSS_VERSION_BUCKETS.size,
                        ),
                        TestCoverage(
                            34,
                            TestType.ALL_VERSIONS_INTEG_MULTI_VERSION,
                            Os.WINDOWS,
                            JvmCategory.MIN_VERSION,
                            ALL_CROSS_VERSION_BUCKETS.size,
                        ),
                        TestCoverage(
                            36,
                            TestType.PLATFORM,
                            Os.MACOS,
                            JvmCategory.MAX_LTS_VERSION,
                            expectedBucketNumber = DEFAULT_MACOS_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AARCH64,
                        ),
                    ),
                docsTests =
                    listOf(
                        DocsTestCoverage(Os.MACOS, JvmCategory.MAX_VERSION, listOf(CONFIG_CACHE_DISABLED)),
                    ),
                performanceTests = slowPerformanceTestCoverages,
                performanceTestPartialTriggers =
                    listOf(
                        PerformanceTestPartialTrigger(
                            "All Performance Tests",
                            "AllPerformanceTests",
                            performanceRegressionTestCoverages + slowPerformanceTestCoverages,
                        ),
                    ),
            ),
            Stage(
                StageName.WEEKLY_VALIDATION,
                trigger = Trigger.WEEKLY,
                runsIndependent = true,
                specificBuilds =
                    listOf(
                        SpecificBuild.GradleceptionWithMaxLtsJdk,
                    ),
                functionalTests =
                    listOf(
                        TestCoverage(
                            37,
                            TestType.CONFIG_CACHE,
                            Os.MACOS,
                            JvmCategory.MAX_VERSION,
                            expectedBucketNumber = DEFAULT_MACOS_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AARCH64,
                        ),
                        TestCoverage(38, TestType.CONFIG_CACHE, Os.WINDOWS, JvmCategory.MAX_VERSION),
                        TestCoverage(
                            39,
                            TestType.CONFIG_CACHE,
                            Os.LINUX,
                            JvmCategory.MAX_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AARCH64,
                        ),
                        TestCoverage(
                            40,
                            TestType.CONFIG_CACHE,
                            Os.LINUX,
                            JvmCategory.MAX_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AMD64,
                        ),
                        TestCoverage(
                            41,
                            TestType.CONFIG_CACHE,
                            Os.LINUX,
                            JvmCategory.MAX_LTS_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AARCH64,
                        ),
                        TestCoverage(
                            42,
                            TestType.CONFIG_CACHE,
                            Os.LINUX,
                            JvmCategory.MAX_LTS_VERSION,
                            DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                            arch = Arch.AMD64,
                        ),
                        TestCoverage(
                            43,
                            TestType.QUICK,
                            Os.ALPINE,
                            JvmCategory.MAX_VERSION,
                            expectedBucketNumber = DEFAULT_LINUX_FUNCTIONAL_TEST_BUCKET_SIZE,
                        ),
                    ),
            ),
            Stage(
                StageName.HISTORICAL_PERFORMANCE,
                trigger = if (branch.isLegacyRelease) Trigger.NEVER else Trigger.WEEKLY,
                runsIndependent = true,
                performanceTests =
                    listOf(
                        PerformanceTestCoverage(
                            4,
                            PerformanceTestType.FLAKINESS_DETECTION,
                            Os.LINUX,
                            numberOfBuckets = 60,
                            oldUuid = "PerformanceTestFlakinessDetectionLinux",
                        ),
                        PerformanceTestCoverage(
                            15,
                            PerformanceTestType.FLAKINESS_DETECTION,
                            Os.WINDOWS,
                            numberOfBuckets = 10,
                        ),
                        PerformanceTestCoverage(
                            16,
                            PerformanceTestType.FLAKINESS_DETECTION,
                            Os.MACOS,
                            numberOfBuckets = 10,
                        ),
                    ),
            ),
        ),
    val subprojects: GradleSubprojectProvider,
)

fun TestCoverage.getBucketUuid(
    model: CIBuildModel,
    bucketIndex: Int,
) = asConfigurationId(model, "bucket${bucketIndex + 1}")

interface BuildTypeBucket {
    fun createFunctionalTestsFor(
        model: CIBuildModel,
        stage: Stage,
        testCoverage: TestCoverage,
        bucketIndex: Int,
    ): FunctionalTest

    fun getName(testCoverage: TestCoverage): String = throw UnsupportedOperationException()

    fun getDescription(testCoverage: TestCoverage): String = throw UnsupportedOperationException()
}

data class GradleSubproject(
    val name: String,
    val path: String,
    val unitTests: Boolean = true,
    val functionalTests: Boolean = true,
    val crossVersionTests: Boolean = false,
) {
    fun hasTestsOf(testType: TestType) =
        (unitTests && testType.unitTests) ||
            (functionalTests && testType.functionalTests) ||
            (crossVersionTests && testType.crossVersionTests)
}

data class Stage(
    val stageName: StageName,
    val specificBuilds: List<SpecificBuild> = emptyList(),
    val functionalTests: List<TestCoverage> = emptyList(),
    val docsTests: List<DocsTestCoverage> = emptyList(),
    val performanceTests: List<PerformanceTestCoverage> = emptyList(),
    val performanceTestPartialTriggers: List<PerformanceTestPartialTrigger> = emptyList(),
    val flameGraphs: List<FlameGraphGeneration> = emptyList(),
    val trigger: Trigger = Trigger.NEVER,
    val runsIndependent: Boolean = false,
) {
    val id = stageName.id
}

data class DocsTestCoverage(
    val os: Os,
    val testJava: JvmCategory,
    val docsTestTypes: List<DocsTestType>,
)

data class TestCoverage(
    val uuid: Int,
    val testType: TestType,
    val os: Os,
    val testJvmVersion: JvmVersion,
    val vendor: JvmVendor = JvmVendor.ORACLE,
    val buildJvm: Jvm = BuildToolBuildJvm,
    val expectedBucketNumber: Int = DEFAULT_FUNCTIONAL_TEST_BUCKET_SIZE,
    val arch: Arch = os.defaultArch,
    val failStage: Boolean = true,
) {
    constructor(
        uuid: Int,
        testType: TestType,
        os: Os,
        testJvm: JvmCategory,
        expectedBucketNumber: Int = DEFAULT_FUNCTIONAL_TEST_BUCKET_SIZE,
        buildJvm: Jvm = BuildToolBuildJvm,
        arch: Arch = Arch.AMD64,
        failStage: Boolean = true,
    ) : this(
        uuid,
        testType,
        os,
        testJvm.version,
        testJvm.vendor,
        buildJvm,
        expectedBucketNumber,
        arch,
        failStage,
    )

    fun asId(projectId: String): String = "${projectId}_$testCoveragePrefix"

    fun asId(model: CIBuildModel): String = asId(model.projectId)

    private val testCoveragePrefix
        get() = "${testType.name.toCamelCase().toCapitalized()}_$uuid"

    fun asConfigurationId(
        model: CIBuildModel,
        subProject: String = "",
    ): String {
        val prefix = "${testCoveragePrefix}_"
        val shortenedSubprojectName = shortenSubprojectName(model.projectId, prefix + subProject)
        return model.projectId + "_" + if (subProject.isNotEmpty()) shortenedSubprojectName else "${prefix}0"
    }

    private fun shortenSubprojectName(
        prefix: String,
        subProjectName: String,
    ): String {
        val shortenedSubprojectName = subProjectName.replace("internal", "i").replace("Testing", "T")
        if (shortenedSubprojectName.length + prefix.length <= 80) {
            return shortenedSubprojectName
        }
        return shortenedSubprojectName.replace(Regex("[aeiou]"), "")
    }

    fun asName(): String =
        listOf(
            testType.name
                .lowercase()
                .toCamelCase()
                .toCapitalized(),
            testJvmVersion.toCapitalized(),
            vendor.displayName,
            os.asName(),
            arch.asName(),
        ).joinToString(" ")
}

enum class TestType(
    val unitTests: Boolean = true,
    val functionalTests: Boolean = true,
    val crossVersionTests: Boolean = false,
    val timeout: Int = 180,
    val maxParallelForks: Int = 4,
) {
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    QUICK(true, true, true, 120, 4),

    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    PLATFORM(true, true, true),

    // Cross version tests select a small set of versions to cover when run as part of this stage
    QUICK_FEEDBACK_CROSS_VERSION(false, false, true),

    // Cross version tests select all versions to cover when run as part of this stage
    ALL_VERSIONS_CROSS_VERSION(false, false, true, 240),

    // run integMultiVersionTest with all version to cover
    ALL_VERSIONS_INTEG_MULTI_VERSION(false, true, false),
    PARALLEL(false, true, false),

    NO_DAEMON(false, true, false, 360),
    CONFIG_CACHE(false, true, false),
    ISOLATED_PROJECTS(false, true, false),
    SOAK(false, false, false),
    FORCE_REALIZE_DEPENDENCY_MANAGEMENT(false, true, false),
    ;

    fun asCamelCase() = name.lowercase().toCamelCase()
}

enum class PerformanceTestType(
    val displayName: String,
    val timeout: Int,
    val defaultBaselines: String = "",
    val channel: String,
    val extraParameters: String = "",
) {
    PER_COMMIT(
        displayName = "Performance Regression Test",
        timeout = 420,
        channel = "commits",
    ),
    PER_DAY(
        displayName = "Slow Performance Regression Test",
        timeout = 420,
        channel = "commits",
    ),
    PER_WEEK(
        displayName = "Performance Experiment",
        timeout = 420,
        channel = "experiments",
    ),
    FLAKINESS_DETECTION(
        displayName = "Performance Test Flakiness Detection",
        timeout = 600,
        defaultBaselines = "flakiness-detection-commit",
        channel = "flakiness-detection",
        extraParameters = "--checks none --rerun --cross-version-only",
    ),
    AD_HOC(
        displayName = "AdHoc Performance Test",
        timeout = 30,
        channel = "adhoc",
        extraParameters = "--checks none",
    ),
}

enum class Trigger {
    NEVER,
    EACH_COMMIT,
    DAILY,
    WEEKLY,
}

const val GRADLE_BUILD_SMOKE_TEST_NAME = "gradleBuildSmokeTest"

enum class SpecificBuild {
    CompileAll {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = CompileAll(model, stage)
    },
    SanityCheck {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SanityCheck(model, stage)
    },
    BuildLogicTest {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = BuildLogicTest(model, stage)
    },
    BuildDistributions {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = BuildDistributions(model, stage)
    },
    Gradleception {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = Gradleception(model, stage, BuildToolBuildJvm, "Default")
    },
    GradleceptionWithMaxLtsJdk {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = Gradleception(model, stage, JvmCategory.MAX_LTS_VERSION, "MaxLts")
    },
    CheckLinks {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = CheckLinks(model, stage)
    },
    CheckTeamCityKotlinDSL {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = CheckTeamCityKotlinDSL(model, stage)
    },
    TestPerformanceTest {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = TestPerformanceTest(model, stage)
    },
    SmokeTestsMinJavaVersion {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SmokeTests(model, stage, JvmCategory.MIN_VERSION, name, splitNumber = 2)
    },
    SmokeTestsMaxJavaVersion {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SmokeTests(model, stage, JvmCategory.MAX_LTS_VERSION, name, splitNumber = 4)
    },
    SantaTrackerSmokeTests {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType =
            SmokeTests(model, stage, JvmCategory.SANTA_TRACKER_SMOKE_TEST_VERSION, name, "santaTrackerSmokeTest", 4)
    },
    ConfigCacheSantaTrackerSmokeTests {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType =
            SmokeTests(
                model,
                stage,
                JvmCategory.SANTA_TRACKER_SMOKE_TEST_VERSION,
                name,
                "configCacheSantaTrackerSmokeTest",
                4,
            )
    },
    GradleBuildSmokeTests {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType =
            SmokeTests(model, stage, JvmCategory.MAX_LTS_VERSION, name, GRADLE_BUILD_SMOKE_TEST_NAME, splitNumber = 4)
    },
    ConfigCacheSmokeTestsMinJavaVersion {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SmokeTests(model, stage, JvmCategory.MIN_VERSION, name, "configCacheSmokeTest", splitNumber = 4)
    },
    ConfigCacheSmokeTestsMaxJavaVersion {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SmokeTests(model, stage, JvmCategory.MAX_LTS_VERSION, name, "configCacheSmokeTest", splitNumber = 4)
    },
    SmokeIdeTests {
        override fun create(
            model: CIBuildModel,
            stage: Stage,
        ): OsAwareBaseGradleBuildType = SmokeIdeTests(model, stage)
    }, ;

    abstract fun create(
        model: CIBuildModel,
        stage: Stage,
    ): OsAwareBaseGradleBuildType
}
