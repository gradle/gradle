package model

import common.BuildCache
import common.JvmCategory
import common.JvmVendor
import common.JvmVersion
import common.Os
import common.builtInRemoteBuildCacheNode
import configurations.BuildDistributions
import configurations.CompileAll
import configurations.DependenciesCheck
import configurations.Gradleception
import configurations.SanityCheck
import configurations.SmokeTests
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.ErrorConsumer
import jetbrains.buildServer.configs.kotlin.v2018_2.Validatable

enum class StageNames(override val stageName: String, override val description: String, override val uuid: String) : StageName {
    QUICK_FEEDBACK_LINUX_ONLY("Quick Feedback - Linux Only", "Run checks and functional tests (embedded executer, Linux)", "QuickFeedbackLinuxOnly"),
    QUICK_FEEDBACK("Quick Feedback", "Run checks and functional tests (embedded executer, Windows)", "QuickFeedback"),
    READY_FOR_MERGE("Ready for Merge", "Run performance and functional tests (against distribution)", "BranchBuildAccept"),
    READY_FOR_NIGHTLY("Ready for Nightly", "Rerun tests in different environments / 3rd party components", "MasterAccept"),
    READY_FOR_RELEASE("Ready for Release", "Once a day: Rerun tests in more environments", "ReleaseAccept"),
    HISTORICAL_PERFORMANCE("Historical Performance", "Once a week: Run performance tests for multiple Gradle versions", "HistoricalPerformance"),
    EXPERIMENTAL("Experimental", "On demand: Run experimental tests", "Experimental"),
}

data class CIBuildModel(
    val projectPrefix: String = "Gradle_Check_",
    val rootProjectName: String = "Check",
    val tagBuilds: Boolean = true,
    val publishStatusToGitHub: Boolean = true,
    val masterAndReleaseBranches: List<String> = listOf("master", "release"),
    val parentBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val childBuildCache: BuildCache = builtInRemoteBuildCacheNode,
    val buildScanTags: List<String> = emptyList(),
    val stages: List<Stage> = listOf(
        Stage(StageNames.QUICK_FEEDBACK_LINUX_ONLY,
            specificBuilds = listOf(
                SpecificBuild.CompileAll, SpecificBuild.SanityCheck),
            functionalTests = listOf(
                TestCoverage(1, TestType.quick, Os.linux, common.JvmCategory.MAX_VERSION.version, vendor = common.JvmCategory.MAX_VERSION.vendor)), omitsSlowProjects = true),
        Stage(StageNames.QUICK_FEEDBACK,
            functionalTests = listOf(
                TestCoverage(2, TestType.quick, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
            functionalTestsDependOnSpecificBuilds = true,
            omitsSlowProjects = true,
            dependsOnSanityCheck = true),
        Stage(StageNames.READY_FOR_MERGE,
            specificBuilds = listOf(
                SpecificBuild.BuildDistributions,
                SpecificBuild.Gradleception,
                SpecificBuild.SmokeTests),
            functionalTests = listOf(
                TestCoverage(3, TestType.platform, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(4, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)),
            performanceTests = listOf(PerformanceTestType.test),
            omitsSlowProjects = true),
        Stage(StageNames.READY_FOR_NIGHTLY,
            trigger = Trigger.eachCommit,
            functionalTests = listOf(
                TestCoverage(5, TestType.quickFeedbackCrossVersion, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(6, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(7, TestType.parallel, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))
        ),
        Stage(StageNames.READY_FOR_RELEASE,
            trigger = Trigger.daily,
            functionalTests = listOf(
                TestCoverage(8, TestType.soak, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(9, TestType.soak, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(10, TestType.allVersionsCrossVersion, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(11, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(12, TestType.noDaemon, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(13, TestType.noDaemon, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(14, TestType.platform, Os.macos, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(15, TestType.forceRealizeDependencyManagement, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
            performanceTests = listOf(
                PerformanceTestType.experiment)),
        Stage(StageNames.HISTORICAL_PERFORMANCE,
            trigger = Trigger.weekly,
            performanceTests = listOf(
                PerformanceTestType.historical, PerformanceTestType.flakinessDetection)),
        Stage(StageNames.EXPERIMENTAL,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(16, TestType.quick, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(17, TestType.quick, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(18, TestType.platform, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(19, TestType.platform, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor))
        )
    ),

    val subProjects: List<GradleSubproject> = listOf(
        GradleSubproject("announce"),
        GradleSubproject("antlr"),
        GradleSubproject("baseServices"),
        GradleSubproject("baseServicesGroovy", functionalTests = false),
        GradleSubproject("bootstrap", unitTests = false, functionalTests = false),
        GradleSubproject("buildCache"),
        GradleSubproject("buildCacheHttp"),
        GradleSubproject("buildCachePackaging"),
        GradleSubproject("buildComparison"),
        GradleSubproject("buildProfile"),
        GradleSubproject("buildOption"),
        GradleSubproject("buildInit"),
        GradleSubproject("cli", functionalTests = false),
        GradleSubproject("codeQuality"),
        GradleSubproject("compositeBuilds"),
        GradleSubproject("core", crossVersionTests = true),
        GradleSubproject("coreApi", functionalTests = false),
        GradleSubproject("dependencyManagement", crossVersionTests = true),
        GradleSubproject("diagnostics"),
        GradleSubproject("ear"),
        GradleSubproject("execution"),
        GradleSubproject("fileCollections"),
        GradleSubproject("files", functionalTests = false),
        GradleSubproject("hashing", functionalTests = false),
        GradleSubproject("ide", crossVersionTests = true),
        GradleSubproject("ideNative"),
        GradleSubproject("idePlay"),
        GradleSubproject("instantExecution"),
        GradleSubproject("instantExecutionReport", unitTests = false, functionalTests = false),
        GradleSubproject("integTest", crossVersionTests = true),
        GradleSubproject("internalIntegTesting"),
        GradleSubproject("internalPerformanceTesting"),
        GradleSubproject("internalTesting", functionalTests = false),
        GradleSubproject("ivy", crossVersionTests = true),
        GradleSubproject("jacoco"),
        GradleSubproject("javascript"),
        GradleSubproject("jvmServices", functionalTests = false),
        GradleSubproject("languageGroovy"),
        GradleSubproject("languageJava", crossVersionTests = true),
        GradleSubproject("languageJvm"),
        GradleSubproject("languageNative"),
        GradleSubproject("languageScala"),
        GradleSubproject("launcher"),
        GradleSubproject("logging"),
        GradleSubproject("maven", crossVersionTests = true),
        GradleSubproject("messaging"),
        GradleSubproject("modelCore"),
        GradleSubproject("modelGroovy"),
        GradleSubproject("native"),
        GradleSubproject("osgi"),
        GradleSubproject("persistentCache"),
        GradleSubproject("pineapple", unitTests = false, functionalTests = false),
        GradleSubproject("platformBase"),
        GradleSubproject("platformJvm"),
        GradleSubproject("platformNative"),
        GradleSubproject("platformPlay", containsSlowTests = true),
        GradleSubproject("pluginDevelopment"),
        GradleSubproject("pluginUse", crossVersionTests = true),
        GradleSubproject("plugins"),
        GradleSubproject("processServices"),
        GradleSubproject("publish"),
        GradleSubproject("reporting"),
        GradleSubproject("resources"),
        GradleSubproject("resourcesGcs"),
        GradleSubproject("resourcesHttp"),
        GradleSubproject("resourcesS3"),
        GradleSubproject("resourcesSftp"),
        GradleSubproject("scala"),
        GradleSubproject("signing"),
        GradleSubproject("snapshots"),
        GradleSubproject("testKit"),
        GradleSubproject("testingBase"),
        GradleSubproject("testingJvm"),
        GradleSubproject("testingJunitPlatform"),
        GradleSubproject("testingNative"),
        GradleSubproject("toolingApi", crossVersionTests = true),
        GradleSubproject("toolingApiBuilders", functionalTests = false),
        GradleSubproject("toolingNative", unitTests = false, functionalTests = false, crossVersionTests = true),
        GradleSubproject("versionControl"),
        GradleSubproject("workers"),
        GradleSubproject("workerProcesses", unitTests = false, functionalTests = false),
        GradleSubproject("wrapper", crossVersionTests = true),

        GradleSubproject("soak", unitTests = false, functionalTests = false),

        GradleSubproject("apiMetadata", unitTests = false, functionalTests = false),
        GradleSubproject("kotlinDsl", unitTests = true, functionalTests = true),
        GradleSubproject("kotlinDslProviderPlugins", unitTests = true, functionalTests = true),
        GradleSubproject("kotlinDslToolingModels", unitTests = false, functionalTests = false),
        GradleSubproject("kotlinDslToolingBuilders", unitTests = true, functionalTests = true, crossVersionTests = true),
        GradleSubproject("kotlinDslPlugins", unitTests = true, functionalTests = true),
        GradleSubproject("kotlinDslTestFixtures", unitTests = true, functionalTests = false),
        GradleSubproject("kotlinDslIntegTests", unitTests = false, functionalTests = true),
        GradleSubproject("kotlinCompilerEmbeddable", unitTests = false, functionalTests = false),

        GradleSubproject("architectureTest", unitTests = false, functionalTests = false),
        GradleSubproject("distributionsDependencies", unitTests = false, functionalTests = false),
        GradleSubproject("buildScanPerformance", unitTests = false, functionalTests = false),
        GradleSubproject("distributions", unitTests = false, functionalTests = false),
        GradleSubproject("docs", unitTests = false, functionalTests = false),
        GradleSubproject("installationBeacon", unitTests = false, functionalTests = false),
        GradleSubproject("internalAndroidPerformanceTesting", unitTests = false, functionalTests = false),
        GradleSubproject("performance", unitTests = false, functionalTests = false),
        GradleSubproject("runtimeApiInfo", unitTests = false, functionalTests = false),
        GradleSubproject("smokeTest", unitTests = false, functionalTests = false))
) {
    val buildTypeBuckets: List<BuildTypeBucket>

    init {
        val subprojectMap = subProjects.map { it.name to it }.toMap()
        val buckets = listOf(
            listOf("resources", "resourcesGcs", "resourcesHttp", "resourcesS3", "resourcesSftp"),
            listOf("platformBase", "platformJvm", "platformNative")
        )
        val largeSubprojects = mapOf("integTest" to 3, "core" to 3, "dependencyManagement" to 3)

        buildTypeBuckets =
            listOf<BuildTypeBucket>(
                SubprojectBucket(name = "AllUnitTest", subprojects = subProjects.filter { it.hasOnlyUnitTests() })
            ) + buckets.map { projectsInBucket ->
                SubprojectBucket(name = projectsInBucket[0], subprojects = projectsInBucket.map { subprojectMap.getValue(it) })
            } + largeSubprojects.flatMap { entry ->
                (1..entry.value).map { SubprojectSplit(subproject = subprojectMap.getValue(entry.key), number = it, total = entry.value) }
            } + subProjects.filter {
                !buckets.flatten().contains(it.name) && !largeSubprojects.containsKey(it.name) && !it.hasOnlyUnitTests()
            }
    }
}

interface BuildTypeBucket {
    // TODO: Hacky. We should really be running all the subprojects on macOS
    // But we're restricting this to just a subset of projects for now
    // since we only have a small pool of macOS agents
    fun shouldBeSkipped(testCoverage: TestCoverage): Boolean

    val name: String

    fun containsSlowTests(): Boolean
    fun shouldBeSkippedInStage(stage: Stage): Boolean
    fun hasTestsOf(testType: TestType): Boolean
    fun extraParameters(): String
    fun getSubprojectNames(): List<String>
}

data class SubprojectSplit(val subproject: GradleSubproject, val number: Int, val total: Int) : BuildTypeBucket by subproject, Validatable {
    override fun validate(consumer: ErrorConsumer) {
        if (total <= 1) {
            consumer.consumeError("Split number must be > 1: ${subproject.name} $total!")
        }
    }

    override val name: String
        get() = if (number == 1) subproject.name else "${subproject.name}_$number"

    override fun extraParameters(): String = "-PtestSplit=$number/$total"
}

data class SubprojectBucket(override val name: String, val subprojects: List<GradleSubproject>) : BuildTypeBucket, Validatable {
    override fun getSubprojectNames(): List<String> {
        return subprojects.map { it.name }
    }

    override fun shouldBeSkippedInStage(stage: Stage) = stage.omitsSlowProjects && subprojects.any { it.containsSlowTests }

    override fun validate(consumer: ErrorConsumer) {
        if (!hasSameProperties { it.unitTests }
            || !hasSameProperties { it.functionalTests }
            || !hasSameProperties { it.crossVersionTests }) {
            consumer.consumeError("All merged subprojects must have same properties: ${subprojects.map { it.name }.joinToString(" ")}")
        }

        Os.values().forEach {
            val intersected = subprojects.intersect(it.ignoredSubprojects)
            if (intersected.isNotEmpty() && intersected.size != subprojects.size) {
                consumer.consumeError("Either all subprojects in a bucket are ignored, or none of them are ignored")
            }
        }
    }

    private
    fun hasSameProperties(predicate: (GradleSubproject) -> Boolean): Boolean {
        val count = subprojects.count(predicate)
        return count == 0 || count == subprojects.size
    }

    override fun shouldBeSkipped(testCoverage: TestCoverage) = subprojects.any { it.shouldBeSkipped(testCoverage) }

    override fun containsSlowTests() = subprojects.any { it.containsSlowTests }

    override fun hasTestsOf(testType: TestType) = subprojects.any { it.hasTestsOf(testType) }

    override fun extraParameters() = ""
}


data class GradleSubproject(override val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val containsSlowTests: Boolean = false) : BuildTypeBucket {
    override fun getSubprojectNames(): List<String> {
        return listOf(name)
    }

    override fun shouldBeSkippedInStage(stage: Stage) = containsSlowTests && stage.omitsSlowProjects

    override fun shouldBeSkipped(testCoverage: TestCoverage) = testCoverage.os.ignoredSubprojects.contains(name)

    override fun containsSlowTests() = containsSlowTests

    override fun hasTestsOf(testType: TestType) = (unitTests && testType.unitTests) || (functionalTests && testType.functionalTests) || (crossVersionTests && testType.crossVersionTests)

    override fun extraParameters() = ""

    fun asDirectoryName(): String {
        return name.replace(Regex("([A-Z])")) { "-" + it.groups[1]!!.value.toLowerCase() }
    }

    fun hasOnlyUnitTests() = unitTests && !functionalTests && !crossVersionTests
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

data class TestCoverage(val uuid: Int, val testType: TestType, val os: Os, val testJvmVersion: JvmVersion, val vendor: JvmVendor = JvmVendor.oracle, val buildJvmVersion: JvmVersion = JvmVersion.java11) {
    fun asId(model: CIBuildModel): String {
        return "${model.projectPrefix}$testCoveragePrefix"
    }

    private
    val testCoveragePrefix
        get() = "${testType.name.capitalize()}_$uuid"

    fun asConfigurationId(model: CIBuildModel, subproject: String = ""): String {
        val prefix = "${testCoveragePrefix}_"
        val shortenedSubprojectName = shortenSubprojectName(model.projectPrefix, prefix + subproject)
        val ret = model.projectPrefix + if (!subproject.isEmpty()) shortenedSubprojectName else "${prefix}0"
        return ret;
    }

    private
    fun shortenSubprojectName(prefix: String, subprojectName: String): String {
        val shortenedSubprojectName = subprojectName.replace("internal", "i").replace("Testing", "T")
        if (shortenedSubprojectName.length + prefix.length <= 80) {
            return shortenedSubprojectName
        }
        return shortenedSubprojectName.replace(Regex("[aeiou]"), "")
    }

    fun asName(): String {
        return "Test Coverage - ${testType.name.capitalize()} ${testJvmVersion.name.capitalize()} ${vendor.name.capitalize()} ${os.name.capitalize()}"
    }
}

enum class TestType(val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val timeout: Int = 180) {
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    quick(true, true, true, 60),
    // Include cross version tests, these take care of selecting a very small set of versions to cover when run as part of this stage, including the current version
    platform(true, true, true),
    // Cross version tests select a small set of versions to cover when run as part of this stage
    quickFeedbackCrossVersion(false, false, true),
    // Cross version tests select all versions to cover when run as part of this stage
    allVersionsCrossVersion(false, true, true, 240),
    parallel(false, true, false),
    noDaemon(false, true, false, 240),
    soak(false, false, false),
    forceRealizeDependencyManagement(false, true, false)
}

enum class PerformanceTestType(val taskId: String, val timeout: Int, val defaultBaselines: String = "", val extraParameters: String = "", val hasRerunner: Boolean = true) {
    test("PerformanceTest", 420, "defaults"),
    experiment("PerformanceExperiment", 420, "defaults"),
    flakinessDetection("FlakinessDetection", 420, "flakiness-detection-commit", hasRerunner = false),
    historical("FullPerformanceTest", 2280, "2.14.1,3.5.1,4.0,last", "--checks none", hasRerunner = false);

    fun asId(model: CIBuildModel): String {
        return "${model.projectPrefix}Performance${name.capitalize()}Coordinator"
    }
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
    SmokeTests {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage)
        }
    },
    DependenciesCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return DependenciesCheck(model, stage)
        }
    };

    abstract fun create(model: CIBuildModel, stage: Stage): BuildType
}
