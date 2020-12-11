import common.VersionedSettingsBranch
import model.CROSS_VERSION_BUCKETS
import model.FunctionalTestBucketProvider
import model.JsonBasedGradleSubprojectProvider
import model.StatisticBasedFunctionalTestBucketProvider
import model.ignoredSubprojects
import common.JvmVendor
import common.JvmVersion
import common.Os
import configurations.FunctionalTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnText
import model.CIBuildModel
import model.Stage
import model.StageNames
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import projects.FunctionalTestProject
import projects.CheckProject
import projects.StageProject
import java.io.File

class CIConfigIntegrationTests {
    private val subprojectProvider = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    private val model = CIBuildModel(
        projectId = "Gradle_Check",
        branch = VersionedSettingsBranch.MASTER,
        buildScanTags = listOf("Check"),
        subprojects = subprojectProvider
    )
    private val gradleBuildBucketProvider = StatisticBasedFunctionalTestBucketProvider(model, File("./test-class-data.json").absoluteFile)
    private val rootProject = CheckProject(model, gradleBuildBucketProvider)

    private
    fun Project.searchSubproject(id: String): StageProject = (subProjects.find { it.id!!.value == id } as StageProject)

    @Test
    fun configurationTreeCanBeGenerated() {
        assertEquals(rootProject.subProjects.size, model.stages.size)
        assertEquals(rootProject.buildTypes.size, model.stages.size)
    }

    @Test
    fun macBuildsHasEmptyRepoMirrorUrlsParam() {
        val rootProject = CheckProject(model, gradleBuildBucketProvider)
        val readyForRelease = rootProject.searchSubproject("Gradle_Check_Stage_ReadyforRelease")
        val macBuilds = readyForRelease.subProjects.filter { it.name.contains("Macos") }.flatMap { (it as FunctionalTestProject).functionalTests }
        assertTrue(macBuilds.isNotEmpty())
        assertTrue(macBuilds.all { it.params.findRawParam("env.REPO_MIRROR_URLS")!!.value == "" })
    }

    @Test
    fun macOSBuildsSubset() {
        val readyForRelease = rootProject.subProjects.find { it.name.contains(StageNames.READY_FOR_RELEASE.stageName) }!!
        val macOS = readyForRelease.subProjects.find { it.name.contains("Macos") }!!

        macOS.buildTypes.forEach { buildType ->
            assertFalse(Os.MACOS.ignoredSubprojects.any { subProject ->
                buildType.name.endsWith("($subProject)")
            })
        }
    }

    @Test
    fun configurationsHaveDependencies() {
        val stagePassConfigs = rootProject.buildTypes
        stagePassConfigs.forEach {
            val stageNumber = stagePassConfigs.indexOf(it) + 1
            if (stageNumber <= model.stages.size) {
                val stage = model.stages[stageNumber - 1]
                val prevStage = if (stageNumber > 1) model.stages[stageNumber - 2] else null
                var functionalTestCount = 0

                if (stage.runsIndependent) {
                    return@forEach
                }

                stage.functionalTests.forEach { testCoverage ->
                    functionalTestCount += if (testCoverage.testDistribution) 1 else gradleBuildBucketProvider.createFunctionalTestsFor(stage, testCoverage).size
                    if (testCoverage.testType == TestType.soak) {
                        functionalTestCount++
                    }
                }

                assertEquals(
                    stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + (if (prevStage != null) 1 else 0),
                    it.dependencies.items.size, stage.stageName.stageName)
            } else {
                assertEquals(2, it.dependencies.items.size) // Individual Performance Worker
            }
        }
    }

    class SubProjectBucketProvider(private val model: CIBuildModel) : FunctionalTestBucketProvider {
        override fun createFunctionalTestsFor(stage: Stage, testCoverage: TestCoverage) =
            model.subprojects.subprojects.map { it.createFunctionalTestsFor(model, stage, testCoverage, Int.MAX_VALUE) }
    }

    private
    fun Project.searchBuildProject(id: String): StageProject = (subProjects.find { it.id!!.value == id } as StageProject)

    private
    val largeSubProjectRegex = """\((\w+(_\d+))\)""".toRegex()

    /**
     * Test Coverage - AllVersionsCrossVersion Java8 Oracle Linux (core_2) -> core_2
     */
    private
    fun FunctionalTest.getSubProjectSplitName() = largeSubProjectRegex.find(this.name)!!.groupValues[1]

    private
    fun FunctionalTest.getGradleTasks(): String {
        val runnerStep = this.steps.items.find { it.name == "GRADLE_RUNNER" } as GradleBuildStep
        return runnerStep.tasks!!
    }

    private
    fun FunctionalTest.getGradleParams(): String {
        val runnerStep = this.steps.items.find { it.name == "GRADLE_RUNNER" } as GradleBuildStep
        return runnerStep.gradleParams!!
    }

    @Test
    fun canSplitLargeProjects() {
        fun assertAllSplitsArePresent(subProjectName: String, functionalTests: List<FunctionalTest>) {
            val splitSubProjectNames = functionalTests.map { it.getSubProjectSplitName() }.toSet()
            val expectedProjectNames = (1..functionalTests.size).map { "${subProjectName}_$it" }.toSet()
            assertEquals(expectedProjectNames, splitSubProjectNames)
        }

        fun assertCorrectParameters(subProjectName: String, functionalTests: List<FunctionalTest>) {
            functionalTests.forEach { assertTrue(it.getGradleTasks().contains(":$subProjectName")) }
            if (functionalTests.size == 1) {
                assertFalse(functionalTests[0].getGradleParams().contains("-PincludeTestClasses"))
                assertFalse(functionalTests[0].getGradleParams().contains("-PexcludeTestClasses"))
            } else {
                functionalTests.forEachIndexed { index, it ->
                    if (index == functionalTests.size - 1) {
                        assertTrue(it.getGradleParams().contains("-PexcludeTestClasses"))
                    } else {
                        assertTrue(it.getGradleParams().contains("-PincludeTestClasses"))
                    }
                }
            }
        }

        fun assertProjectAreSplitByClassesCorrectly(functionalTests: List<FunctionalTest>) {
            val functionalTestsWithSplit: Map<String, List<FunctionalTest>> = functionalTests.filter { largeSubProjectRegex.containsMatchIn(it.name) }.groupBy { it.getSubProjectSplitName().substringBefore('_') }
            functionalTestsWithSplit.forEach {
                assertAllSplitsArePresent(it.key, it.value)
                assertCorrectParameters(it.key, it.value)
            }
        }

        fun assertProjectAreSplitByGradleVersionCorrectly(testType: TestType, functionalTests: List<FunctionalTest>) {
            CROSS_VERSION_BUCKETS.forEachIndexed { index: Int, startEndVersion: List<String> ->
                assertTrue(functionalTests[index].name.contains("(${startEndVersion[0]} <= gradle <${startEndVersion[1]})"))
                assertEquals(":clean :${testType}Test", functionalTests[index].getGradleTasks())
                assertTrue(functionalTests[index].getGradleParams().contains("-PonlyTestGradleVersion=${startEndVersion[0]}-${startEndVersion[1]}"))
            }
        }

        for (stageProject in rootProject.subProjects.filterIsInstance<StageProject>()) {
            for (functionalTestProject in stageProject.subProjects.filterIsInstance<FunctionalTestProject>()) {
                when {
                    functionalTestProject.name.contains("AllVersionsCrossVersion") -> {
                        assertProjectAreSplitByGradleVersionCorrectly(TestType.allVersionsCrossVersion, functionalTestProject.functionalTests)
                    }
                    functionalTestProject.name.contains("QuickFeedbackCrossVersion") -> {
                        assertProjectAreSplitByGradleVersionCorrectly(TestType.quickFeedbackCrossVersion, functionalTestProject.functionalTests)
                    }
                    else -> {
                        assertProjectAreSplitByClassesCorrectly(functionalTestProject.functionalTests)
                    }
                }
            }
        }
    }

    @Test
    fun onlyReadyForNightlyTriggerHasUpdateBranchStatus() {
        val triggerNameToTasks = rootProject.buildTypes.map { it.id.toString() to ((it as StagePasses).steps.items[0] as GradleBuildStep).tasks }.toMap()
        val readyForNightlyId = toTriggerId("ReadyforNightly")
        assertEquals(":distribution-core:base-services:createBuildReceipt updateBranchStatus", triggerNameToTasks[readyForNightlyId])
        val otherTaskNames = triggerNameToTasks.filterKeys { it != readyForNightlyId }.values.toSet()
        assertEquals(setOf(":distribution-core:base-services:createBuildReceipt"), otherTaskNames)
    }

    @Test
    fun buildsContainFailureConditionForPotentialCredentialsLeaks() {
        val allBuildTypes = rootProject.subProjects.flatMap { it.buildTypes }
        allBuildTypes.forEach {
            val credentialLeakCondition = it.failureConditions.items.find { it.type.equals("BuildFailureOnMessage") } as BuildFailureOnText
            assertTrue(credentialLeakCondition.enabled)
            assertTrue(credentialLeakCondition.stopBuildOnFailure!!)
        }
    }

    private fun toTriggerId(id: String) = "Gradle_Check_Stage_${id}_Trigger"

    @Test
    fun uuidsAreUnique() {
        val uuidList = model.stages.flatMap { it.functionalTests.map { ft -> ft.uuid } }
        assertEquals(uuidList.distinct(), uuidList)
    }

    @Test
    fun long_ids_are_shortened() {
        val testCoverage = TestCoverage(1, TestType.quickFeedbackCrossVersion, Os.WINDOWS, JvmVersion.java11, JvmVendor.oracle)
        val shortenedId = testCoverage.asConfigurationId(model, "veryLongSubprojectNameLongerThanEverythingWeHave")
        assertTrue(shortenedId.length < 80)
        assertEquals("Gradle_Check_QckFdbckCrssVrsn_1_vryLngSbprjctNmLngrThnEvrythngWHv", shortenedId)

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_iIntegT", testCoverage.asConfigurationId(model, "internalIntegTesting"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_buildCache", testCoverage.asConfigurationId(model, "buildCache"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_0", testCoverage.asConfigurationId(model))
    }

    @Test
    fun allVersionsAreIncludedInCrossVersionTests() {
        assertEquals("0.0", CROSS_VERSION_BUCKETS[0][0])
        assertEquals("99.0", CROSS_VERSION_BUCKETS[CROSS_VERSION_BUCKETS.size - 1][1])

        (1 until CROSS_VERSION_BUCKETS.size).forEach {
            assertEquals(CROSS_VERSION_BUCKETS[it - 1][1], CROSS_VERSION_BUCKETS[it][0])
        }
    }
}
