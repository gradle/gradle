import common.JvmVendor
import common.JvmVersion
import common.Os
import common.VersionedSettingsBranch
import configurations.BaseGradleBuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.failureConditions.BuildFailureOnText
import model.ALL_CROSS_VERSION_BUCKETS
import model.CIBuildModel
import model.DefaultFunctionalTestBucketProvider
import model.GradleSubproject
import model.JsonBasedGradleSubprojectProvider
import model.QUICK_CROSS_VERSION_BUCKETS
import model.TestCoverage
import model.TestType
import model.ignoredSubprojects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import projects.CheckProject
import projects.FunctionalTestProject
import projects.StageProject
import java.io.File

class CIConfigIntegrationTests {
    init {
        // Set the project id here, so we can use methods on the DslContext
        DslContext.projectId = AbsoluteId("Gradle_Master")
        DslContext.addParameters("Branch" to "master")
    }

    private val subprojectProvider = JsonBasedGradleSubprojectProvider(File("../.teamcity/subprojects.json"))
    private val model = CIBuildModel(
        projectId = "Check",
        branch = VersionedSettingsBranch.fromDslContext(),
        buildScanTags = listOf("Check"),
        subprojects = subprojectProvider
    )
    private val gradleBuildBucketProvider = DefaultFunctionalTestBucketProvider(model, File("./test-buckets.json").absoluteFile)
    private val rootProject = CheckProject(model, gradleBuildBucketProvider)

    @Test
    fun configurationTreeCanBeGenerated() {
        assertEquals(rootProject.subProjects.size, model.stages.size)
        assertEquals(rootProject.buildTypes.size, model.stages.size)
    }

    @Test
    fun configurationsHaveDependencies() {
        val stagePassConfigs = rootProject.buildTypes
        assertEquals(model.stages.size, stagePassConfigs.size)
        stagePassConfigs.forEach {
            val stageNumber = stagePassConfigs.indexOf(it) + 1
            val stage = model.stages[stageNumber - 1]
            val prevStage = if (stageNumber > 1) model.stages[stageNumber - 2] else null

            if (stage.runsIndependent) {
                return@forEach
            }

            assertEquals(
                stage.specificBuilds.size + stage.functionalTests.size + stage.performanceTests.size + stage.docsTests.size + (if (prevStage != null) 1 else 0),
                it.dependencies.items.size, stage.stageName.stageName
            )
        }
    }

    private
    val largeSubProjectRegex = """\((\w+(_\d+))\)""".toRegex()

    /**
     * Test Coverage - AllVersionsCrossVersion Java8 Oracle Linux (core_2) -> core_2
     */
    private
    fun BaseGradleBuildType.getSubProjectSplitName() = largeSubProjectRegex.find(this.name)!!.groupValues[1]

    private
    fun BaseGradleBuildType.getGradleTasks(): String {
        val runnerStep = this.steps.items.find { it.name == "GRADLE_RUNNER" } as GradleBuildStep
        return runnerStep.tasks!!
    }

    private
    fun BaseGradleBuildType.getGradleParams(): String {
        val runnerStep = this.steps.items.find { it.name == "GRADLE_RUNNER" } as GradleBuildStep
        return runnerStep.gradleParams!!
    }

    @Test
    fun canSplitLargeProjects() {
        fun assertAllSplitsArePresent(subProjectName: String, functionalTests: List<BaseGradleBuildType>) {
            val splitSubProjectNames = functionalTests.map { it.getSubProjectSplitName() }.toSet()
            val expectedProjectNames = (1..functionalTests.size).map { "${subProjectName}_$it" }.toSet()
            assertEquals(expectedProjectNames, splitSubProjectNames)
        }

        fun assertCorrectParameters(subProjectName: String, functionalTests: List<BaseGradleBuildType>) {
            functionalTests.forEach { assertTrue(it.getGradleTasks().startsWith("clean $subProjectName")) }
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

        fun assertProjectAreSplitByClassesCorrectly(functionalTests: List<BaseGradleBuildType>) {
            val functionalTestsWithSplit: Map<String, List<BaseGradleBuildType>> =
                functionalTests.filter { largeSubProjectRegex.containsMatchIn(it.name) }.groupBy { it.getSubProjectSplitName().substringBefore('_') }
            functionalTestsWithSplit.forEach {
                assertAllSplitsArePresent(it.key, it.value)
                assertCorrectParameters(it.key, it.value)
            }
        }

        fun assertProjectAreSplitByGradleVersionCorrectly(buckets: List<List<String>>, testType: TestType, functionalTests: List<BaseGradleBuildType>) {
            buckets.forEachIndexed { index: Int, startEndVersion: List<String> ->
                assertTrue(functionalTests[index].name.contains("(${startEndVersion[0]} <= gradle <${startEndVersion[1]})"))
                assertEquals("clean ${testType}Test", functionalTests[index].getGradleTasks())
                assertTrue(
                    functionalTests[index].getGradleParams().apply {
                        println(this)
                    }.contains("-PonlyTestGradleVersion=${startEndVersion[0]}-${startEndVersion[1]}")
                )
            }
        }

        for (stageProject in rootProject.subProjects.filterIsInstance<StageProject>()) {
            for (functionalTestProject in stageProject.subProjects.filterIsInstance<FunctionalTestProject>()) {
                when {
                    functionalTestProject.name.contains("AllVersionsCrossVersion") -> {
                        assertProjectAreSplitByGradleVersionCorrectly(ALL_CROSS_VERSION_BUCKETS, TestType.allVersionsCrossVersion, functionalTestProject.functionalTests)
                    }
                    functionalTestProject.name.contains("QuickFeedbackCrossVersion") -> {
                        assertProjectAreSplitByGradleVersionCorrectly(QUICK_CROSS_VERSION_BUCKETS, TestType.quickFeedbackCrossVersion, functionalTestProject.functionalTests)
                    }
                    else -> {
                        assertProjectAreSplitByClassesCorrectly(functionalTestProject.functionalTests)
                    }
                }
            }
        }
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

    private fun subProjectFolderList(): List<File> {
        val subProjectFolders = File("../subprojects").listFiles()!!.filter { it.isDirectory }
        assertFalse(subProjectFolders.isEmpty())
        return subProjectFolders
    }

    @Test
    fun allSubprojectsAreListed() {
        val knownSubProjectNames = model.subprojects.subprojects.map { it.asDirectoryName() }
        subProjectFolderList().forEach {
            assertTrue(
                it.name in knownSubProjectNames,
                "Not defined: $it"
            )
        }
    }

    @Test
    fun uuidsAreUnique() {
        val uuidList = model.stages.flatMap { it.functionalTests.map { ft -> ft.uuid } }
        assertEquals(uuidList.distinct(), uuidList)
    }

    private fun getSubProjectFolder(subProject: GradleSubproject): File = File("../subprojects/${subProject.asDirectoryName()}")

    @Test
    fun testsAreCorrectlyConfiguredForAllSubProjects() {
        model.subprojects.subprojects.filter {
            !ignoredSubprojects.contains(it.name)
        }.forEach {
            val dir = getSubProjectFolder(it)
            assertEquals(it.unitTests, File(dir, "src/test").isDirectory, "${it.name}'s unitTests is wrong!")
            assertEquals(it.functionalTests, File(dir, "src/integTest").isDirectory, "${it.name}'s functionalTests is wrong!")
            assertEquals(it.crossVersionTests, File(dir, "src/crossVersionTest").isDirectory, "${it.name}'s crossVersionTests is wrong!")
        }
    }

    @Test
    fun allSubprojectsDefineTheirUnitTestPropertyCorrectly() {
        val projectsWithUnitTests = model.subprojects.subprojects.filter { it.unitTests }
        val projectFoldersWithUnitTests = subProjectFolderList().filter {
            File(it, "src/test").exists() &&
                it.name != "architecture-test" // architecture-test:test is part of Sanity Check
        }
        assertFalse(projectFoldersWithUnitTests.isEmpty())
        projectFoldersWithUnitTests.forEach {
            assertTrue(projectsWithUnitTests.map { it.asDirectoryName() }.contains(it.name), "Contains unit tests: $it")
        }
    }

    private fun containsSrcFileWithString(srcRoot: File, content: String, exceptions: List<String>): Boolean {
        srcRoot.walkTopDown().forEach {
            if (it.extension == "groovy" || it.extension == "java") {
                val text = it.readText()
                if (text.contains(content) && exceptions.all { !text.contains(it) }) {
                    println("Found suspicious test file: $it")
                    return true
                }
            }
        }
        return false
    }

    @Test
    fun allSubprojectsDefineTheirFunctionTestPropertyCorrectly() {
        val projectsWithFunctionalTests = model.subprojects.subprojects.filter { it.functionalTests }
        val projectFoldersWithFunctionalTests = subProjectFolderList().filter {
            File(it, "src/integTest").exists() &&
                it.name != "distributions-integ-tests" && // distributions:integTest is part of Build Distributions
                it.name != "soak" // soak tests have their own test category
        }
        assertFalse(projectFoldersWithFunctionalTests.isEmpty())
        projectFoldersWithFunctionalTests.forEach {
            assertTrue(projectsWithFunctionalTests.map { it.asDirectoryName() }.contains(it.name), "Contains functional tests: $it")
        }
    }

    @Test
    fun allSubprojectsDefineTheirCrossVersionTestPropertyCorrectly() {
        val projectsWithCrossVersionTests = model.subprojects.subprojects.filter { it.crossVersionTests }
        val projectFoldersWithCrossVersionTests = subProjectFolderList().filter { File(it, "src/crossVersionTest").exists() }
        assertFalse(projectFoldersWithCrossVersionTests.isEmpty())
        projectFoldersWithCrossVersionTests.forEach {
            assertTrue(projectsWithCrossVersionTests.map { it.asDirectoryName() }.contains(it.name), "Contains cross-version tests: $it")
        }
    }

    @Test
    fun integTestFolderDoesNotContainCrossVersionTests() {
        val projectFoldersWithFunctionalTests = subProjectFolderList().filter { File(it, "src/integTest").exists() }
        assertFalse(projectFoldersWithFunctionalTests.isEmpty())
        projectFoldersWithFunctionalTests.forEach {
            assertFalse(containsSrcFileWithString(File(it, "src/integTest"), "CrossVersion", listOf("package org.gradle.testkit", "CrossVersionPerformanceTest")))
        }
    }

    @Test
    fun long_ids_are_shortened() {
        val testCoverage = TestCoverage(1, TestType.quickFeedbackCrossVersion, Os.WINDOWS, JvmVersion.java11, JvmVendor.oracle)
        val shortenedId = testCoverage.asConfigurationId(model, "veryLongSubprojectNameLongerThanEverythingWeHave")
        assertTrue(shortenedId.length < 80)
        assertEquals("Check_QckFdbckCrssVrsn_1_vryLngSbprjctNmLngrThnEvrythngWHv", shortenedId)

        assertEquals("Check_QuickFeedbackCrossVersion_1_iIntegT", testCoverage.asConfigurationId(model, "internalIntegTesting"))

        assertEquals("Check_QuickFeedbackCrossVersion_1_buildCache", testCoverage.asConfigurationId(model, "buildCache"))

        assertEquals("Check_QuickFeedbackCrossVersion_1_0", testCoverage.asConfigurationId(model))
    }

    @Test
    fun allVersionsAreIncludedInCrossVersionTests() {
        assertEquals("0.0", ALL_CROSS_VERSION_BUCKETS[0][0])
        assertEquals("99.0", ALL_CROSS_VERSION_BUCKETS[ALL_CROSS_VERSION_BUCKETS.size - 1][1])

        (1 until ALL_CROSS_VERSION_BUCKETS.size).forEach {
            assertEquals(ALL_CROSS_VERSION_BUCKETS[it - 1][1], ALL_CROSS_VERSION_BUCKETS[it][0])
        }
    }
}
