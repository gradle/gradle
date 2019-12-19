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
import configurations.FunctionalTest
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
    WINDOWS_10_EVALUATION_QUICK("Experimental Windows10 Quick", "On demand checks to test Windows 10 agents (quick tests)", "ExperimentalWindows10quick"),
    WINDOWS_10_EVALUATION_PLATFORM("Experimental Windows10 Platform", "On demand checks to test Windows 10 agents (platform tests)", "ExperimentalWindows10platform"),
    EXPERIMENTAL_VFS_RETENTION("Experimental VFS Retention", "On demand checks to run tests with VFS retention enabled", "ExperimentalVfsRetention"),
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
                TestCoverage(1, TestType.quick, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)), omitsSlowProjects = true),
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
                SpecificBuild.SmokeTestsMinJavaVersion,
                SpecificBuild.SmokeTestsMaxJavaVersion,
                SpecificBuild.InstantSmokeTestsMinJavaVersion,
                SpecificBuild.InstantSmokeTestsMaxJavaVersion
            ),
            functionalTests = listOf(
                TestCoverage(3, TestType.platform, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(4, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(20, TestType.instant, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor)),
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
                PerformanceTestType.slow)),
        Stage(StageNames.HISTORICAL_PERFORMANCE,
            trigger = Trigger.weekly,
            performanceTests = listOf(
                PerformanceTestType.historical, PerformanceTestType.flakinessDetection, PerformanceTestType.experiment)),
        Stage(StageNames.EXPERIMENTAL,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(16, TestType.quick, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(17, TestType.quick, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(18, TestType.platform, Os.linux, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor),
                TestCoverage(19, TestType.platform, Os.windows, JvmCategory.EXPERIMENTAL_VERSION.version, vendor = JvmCategory.EXPERIMENTAL_VERSION.vendor))),
        Stage(StageNames.WINDOWS_10_EVALUATION_QUICK,
            trigger = Trigger.never,
            runsIndependent = true,
            disablesBuildCache = true,
            functionalTests = listOf(
                TestCoverage(26, TestType.quick, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))),
        Stage(StageNames.WINDOWS_10_EVALUATION_PLATFORM,
            trigger = Trigger.never,
            runsIndependent = true,
            disablesBuildCache = true,
            functionalTests = listOf(
                TestCoverage(21, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(22, TestType.quickFeedbackCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(23, TestType.soak, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(24, TestType.allVersionsCrossVersion, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(25, TestType.noDaemon, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))),
        Stage(StageNames.EXPERIMENTAL_VFS_RETENTION,
            trigger = Trigger.never,
            runsIndependent = true,
            functionalTests = listOf(
                TestCoverage(27, TestType.vfsRetention, Os.linux, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(28, TestType.vfsRetention, Os.linux, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(29, TestType.vfsRetention, Os.windows, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(30, TestType.vfsRetention, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor),
                TestCoverage(31, TestType.vfsRetention, Os.macos, JvmCategory.MIN_VERSION.version, vendor = JvmCategory.MIN_VERSION.vendor),
                TestCoverage(32, TestType.vfsRetention, Os.macos, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)))
        ),

    val subProjects: List<GradleSubproject> = listOf(
        GradleSubproject("antlr"),
        GradleSubproject("baseServices"),
        GradleSubproject("baseServicesGroovy", functionalTests = false),
        GradleSubproject("bootstrap", unitTests = false, functionalTests = false),
        GradleSubproject("buildCache"),
        GradleSubproject("buildCacheHttp", unitTests = false),
        GradleSubproject("buildCachePackaging", functionalTests = false),
        GradleSubproject("buildEvents"),
        GradleSubproject("buildProfile"),
        GradleSubproject("buildOption", functionalTests = false),
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
        GradleSubproject("idePlay", unitTests = false),
        GradleSubproject("instantExecution"),
        GradleSubproject("instantExecutionReport", unitTests = false, functionalTests = false),
        GradleSubproject("integTest", unitTests = false, crossVersionTests = true),
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
        GradleSubproject("persistentCache"),
        GradleSubproject("pineapple", unitTests = false, functionalTests = false),
        GradleSubproject("platformBase"),
        GradleSubproject("platformJvm"),
        GradleSubproject("platformNative"),
        GradleSubproject("platformPlay", containsSlowTests = true),
        GradleSubproject("pluginDevelopment"),
        GradleSubproject("pluginUse"),
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
        GradleSubproject("security", functionalTests = false),
        GradleSubproject("signing"),
        GradleSubproject("snapshots"),
        GradleSubproject("samples", unitTests = false, functionalTests = true),
        GradleSubproject("testKit"),
        GradleSubproject("testingBase"),
        GradleSubproject("testingJvm"),
        GradleSubproject("testingJunitPlatform", unitTests = false, functionalTests = false),
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
        GradleSubproject("kotlinDslProviderPlugins", unitTests = true, functionalTests = false),
        GradleSubproject("kotlinDslToolingModels", unitTests = false, functionalTests = false),
        GradleSubproject("kotlinDslToolingBuilders", unitTests = true, functionalTests = true, crossVersionTests = true),
        GradleSubproject("kotlinDslPlugins", unitTests = false, functionalTests = true),
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
        val buckets = mapOf(
            "resources" to listOf("resources", "resourcesGcs", "resourcesHttp", "resourcesS3", "resourcesSftp"),
            "platformBase" to listOf("platformBase", "platformJvm", "platformNative"),
            "bucket1" to listOf("kotlinDslProviderPlugins", "buildCachePackaging", "native", "snapshots", "internalPerformanceTesting", "internalIntegTesting", "execution", "publish", "ear", "languageJvm"),
            "bucket2" to listOf("baseServices", "processServices", "messaging", "buildProfile", "modelGroovy"),
            "bucket3" to listOf("javascript", "fileCollections", "buildCache", "toolingNative", "buildCacheHttp"),
            "bucket4" to listOf("antlr", "languageGroovy", "reporting", "diagnostics", "versionControl"),
            "bucket5" to listOf("testingBase", "testingNative", "wrapper", "ideNative"),
            "bucket7" to listOf("jacoco", "idePlay"),
            "bucket8" to listOf("signing", "ivy"),
            "bucket9" to listOf("kotlinDsl", "kotlinDslToolingBuilders"),
            "bucket10" to listOf("modelCore", "ide"),
            "bucket11" to listOf("codeQuality", "persistentCache")
        )
        val largeSubprojects = mapOf(
            "integTest" to 3,
            "core" to 4,
            "dependencyManagement" to 3,
            "toolingApi" to 2,
            "samples" to 2,
            "launcher" to 2,
            "languageJava" to 2)

        val nonTrivialBuckets = listOf<BuildTypeBucket>(
            SubprojectBucket(name = "AllUnitTest", subprojects = subProjects.filter { it.hasOnlyUnitTests() })
        ) + buckets.map { entry ->
            SubprojectBucket(name = entry.key, subprojects = entry.value.map { subprojectMap.getValue(it) })
        } + largeSubprojects.map { entry ->
            SubprojectSplit(subproject = subprojectMap.getValue(entry.key), total = entry.value)
        }
        val handledSubprojects = nonTrivialBuckets.flatMap { it.getSubprojectNames() }
        buildTypeBuckets = nonTrivialBuckets + subProjects.filter { !handledSubprojects.contains(it.name) }
    }
}

interface BuildTypeBucket {
    // TODO: Hacky. We should really be running all the subprojects on macOS
    // But we're restricting this to just a subset of projects for now
    // since we only have a small pool of macOS agents
    fun shouldBeSkipped(testCoverage: TestCoverage): Boolean

    fun containsSlowTests(): Boolean
    fun shouldBeSkippedInStage(stage: Stage): Boolean
    fun hasTestsOf(testType: TestType): Boolean
    fun getSubprojectNames(): List<String>

    fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): List<FunctionalTest>
}

data class SubprojectSplit(val subproject: GradleSubproject, val total: Int) : BuildTypeBucket by subproject, Validatable {
    override fun validate(consumer: ErrorConsumer) {
        if (total <= 1) {
            consumer.consumeError("Split number must be > 1: ${subproject.name} $total!")
        }
    }

    private fun getName(number: Int) = if (number == 1) subproject.name else "${subproject.name}_$number"

    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) =
        (1..total).map { createFunctionalTestsFor(model, stage, testCoverage, getName(it), "-PtestSplit=$it/$total") }

    private fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage, name: String, parameter: String): FunctionalTest = FunctionalTest(model,
        testCoverage.asConfigurationId(model, name),
        "${testCoverage.asName()} ($name)",
        "${testCoverage.asName()} for projects $name",
        testCoverage,
        stage,
        listOf(subproject.name),
        parameter
    )
}

data class SubprojectBucket(val name: String, val subprojects: List<GradleSubproject>) : BuildTypeBucket, Validatable {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage): List<FunctionalTest> {
        val subprojectsForCoverage = subprojects.filter { !it.shouldBeSkipped(testCoverage) }
        return listOf(
            FunctionalTest(model, testCoverage.asConfigurationId(model, name),
                "${testCoverage.asName()} (${subprojectsForCoverage.joinToString(", ") { it.name }})",
                "${testCoverage.asName()} for ${subprojectsForCoverage.joinToString(", ") { it.name }}",
                testCoverage,
                stage,
                subprojectsForCoverage.map { it.name }
            )
        )
    }

    override fun getSubprojectNames(): List<String> {
        return subprojects.map { it.name }
    }

    override fun shouldBeSkippedInStage(stage: Stage) = stage.omitsSlowProjects && subprojects.any { it.containsSlowTests }

    override fun validate(consumer: ErrorConsumer) {
        if (!hasSameProperties { it.unitTests } ||
            !hasSameProperties { it.functionalTests } ||
            !hasSameProperties { it.crossVersionTests }) {
            consumer.consumeError("All merged subprojects must have same properties: ${subprojects.joinToString(" ") { it.name }}")
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

    override fun shouldBeSkipped(testCoverage: TestCoverage) = subprojects.all { it.shouldBeSkipped(testCoverage) }

    override fun containsSlowTests() = subprojects.any { it.containsSlowTests }

    override fun hasTestsOf(testType: TestType) = subprojects.any { it.hasTestsOf(testType) }
}

data class GradleSubproject(val name: String, val unitTests: Boolean = true, val functionalTests: Boolean = true, val crossVersionTests: Boolean = false, val containsSlowTests: Boolean = false) : BuildTypeBucket {
    override fun createFunctionalTestsFor(model: CIBuildModel, stage: Stage, testCoverage: TestCoverage) = listOf(
        FunctionalTest(model,
            testCoverage.asConfigurationId(model, name),
            "${testCoverage.asName()} ($name)",
            "${testCoverage.asName()} for $name",
            testCoverage,
            stage,
            listOf(name)
        ))

    override fun getSubprojectNames(): List<String> {
        return listOf(name)
    }

    override fun shouldBeSkippedInStage(stage: Stage) = containsSlowTests && stage.omitsSlowProjects

    override fun shouldBeSkipped(testCoverage: TestCoverage) = testCoverage.os.ignoredSubprojects.contains(name)

    override fun containsSlowTests() = containsSlowTests

    override fun hasTestsOf(testType: TestType) = (unitTests && testType.unitTests) || (functionalTests && testType.functionalTests) || (crossVersionTests && testType.crossVersionTests)

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

data class Stage(val stageName: StageName, val specificBuilds: List<SpecificBuild> = emptyList(), val performanceTests: List<PerformanceTestType> = emptyList(), val functionalTests: List<TestCoverage> = emptyList(), val trigger: Trigger = Trigger.never, val functionalTestsDependOnSpecificBuilds: Boolean = false, val runsIndependent: Boolean = false, val omitsSlowProjects: Boolean = false, val dependsOnSanityCheck: Boolean = false, val disablesBuildCache: Boolean = false) {
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
        return model.projectPrefix + if (subproject.isNotEmpty()) shortenedSubprojectName else "${prefix}0"
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
    instant(false, true, false),
    vfsRetention(false, true, false),
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
    InstantSmokeTestsMinJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MIN_VERSION, "instantSmokeTest")
        }
    },
    InstantSmokeTestsMaxJavaVersion {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return SmokeTests(model, stage, JvmCategory.MAX_VERSION, "instantSmokeTest")
        }
    },
    DependenciesCheck {
        override fun create(model: CIBuildModel, stage: Stage): BuildType {
            return DependenciesCheck(model, stage)
        }
    };

    abstract fun create(model: CIBuildModel, stage: Stage): BuildType
}
