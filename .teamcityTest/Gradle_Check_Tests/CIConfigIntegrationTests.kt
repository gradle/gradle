import common.JvmCategory
import common.JvmVendor
import common.JvmVersion
import common.NoBuildCache
import common.Os
import configurations.FunctionalTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.GradleBuildStep
import model.CIBuildModel
import model.GradleSubproject
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.SubprojectSplit
import model.TestCoverage
import model.TestType
import model.Trigger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import projects.FunctionalTestProject
import projects.RootProject
import projects.StageProject
import java.io.File

class CIConfigIntegrationTests {
    @Test
    fun configurationTreeCanBeGenerated() {
        val model = CIBuildModel()
        val rootProject = RootProject(model)
        assertEquals(rootProject.subProjects.size, model.stages.size + 1)
        assertEquals(rootProject.buildTypes.size, model.stages.size)
    }

    @Test
    fun macBuildsHasEmptyRepoMirrorUrlsParam() {
        val model = CIBuildModel()
        val rootProject = RootProject(model)
        val readyForRelease = rootProject.searchSubproject("Gradle_Check_Stage_ReadyforRelease")
        val macBuilds = readyForRelease.subProjects.filter { it.name.contains("Macos") }.flatMap { (it as FunctionalTestProject).functionalTests }
        assertTrue(macBuilds.isNotEmpty())
        assertTrue(macBuilds.all { it.params.findRawParam("env.REPO_MIRROR_URLS")!!.value == "" })
    }

    @Test
    fun macOSBuildsSubset() {
        val m = CIBuildModel()
        val p = RootProject(m)
        val readyForRelease = p.subProjects.find { it.name.contains(StageNames.READY_FOR_RELEASE.stageName) }!!
        val macOS = readyForRelease.subProjects.find { it.name.contains("Macos") }!!

        macOS.buildTypes.forEach { buildType ->
            assertFalse(Os.macos.ignoredSubprojects.any { subproject ->
                buildType.name.endsWith("($subproject)")
            })
        }
    }

    @Test
    fun configurationsHaveDependencies() {
        val m = CIBuildModel()
        val p = RootProject(m)
        val stagePassConfigs = p.buildTypes
        stagePassConfigs.forEach {
            val stageNumber = stagePassConfigs.indexOf(it) + 1
            println(it.id)
            it.dependencies.items.forEach {
                println("--> " + it.buildTypeId)
            }
            if (stageNumber <= m.stages.size) {
                val stage = m.stages[stageNumber - 1]
                val prevStage = if (stageNumber > 1) m.stages[stageNumber - 2] else null
                var functionalTestCount = 0

                if (stage.runsIndependent) {
                    return@forEach
                }

                stage.functionalTests.forEach { testCoverage ->
                    m.buildTypeBuckets.filter { bucket ->
                        !bucket.shouldBeSkippedInStage(stage) && !bucket.shouldBeSkipped(testCoverage)
                    }.forEach { subprojectBucket ->
                        if (subprojectBucket.hasTestsOf(testCoverage.testType)) {
                            functionalTestCount += subprojectBucket.createFunctionalTestsFor(m, stage, testCoverage).size
                        }
                    }
                    if (testCoverage.testType == TestType.soak) {
                        functionalTestCount++
                    }
                }

                // hacky way to consider deferred tests
                val deferredTestCount = if (stage.stageName == StageNames.READY_FOR_NIGHTLY) 5 else 0
                assertEquals(
                    stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + (if (prevStage != null) 1 else 0) + deferredTestCount,
                    it.dependencies.items.size, stage.stageName.stageName)
            } else {
                assertEquals(2, it.dependencies.items.size) // Individual Performance Worker
            }
        }
    }

    @Test
    fun canDeactivateBuildCacheAndAdjustCIModel() {
        val m = CIBuildModel(
            projectPrefix = "Gradle_BuildCacheDeactivated_",
            parentBuildCache = NoBuildCache,
            childBuildCache = NoBuildCache,
            stages = listOf(
                Stage(StageNames.QUICK_FEEDBACK,
                    specificBuilds = listOf(
                        SpecificBuild.CompileAll,
                        SpecificBuild.SanityCheck,
                        SpecificBuild.BuildDistributions),
                    functionalTests = listOf(
                        TestCoverage(1, TestType.quick, Os.linux, JvmVersion.java8),
                        TestCoverage(2, TestType.quick, Os.windows, JvmVersion.java11, vendor = JvmVendor.openjdk)),
                    omitsSlowProjects = true)
            )
        )
        val p = RootProject(m)
        printTree(p)
        assertTrue(p.subProjects.size == 1)
    }

    fun Project.searchSubproject(id: String): StageProject = (subProjects.find { it.id!!.value == id } as StageProject)

    @Test
    fun canSplitLargeProjects() {
        val model = CIBuildModel()
        val rootProject = RootProject(model)
        val largeSubprojects = model.buildTypeBuckets.filterIsInstance<SubprojectSplit>()

        fun FunctionalTest.isLargeProjectSplit(): Boolean {
            return largeSubprojects.any { this.name.contains("(${it.subproject.name})") || this.name.contains("(${it.subproject.name}_") }
        }

        fun find(buildTypeName: String): String {
            // Test Coverage - AllVersionsCrossVersion Java8 Oracle Linux (core_2) -> core_2
            return """\((\w+(_\d+)?)\)""".toRegex().find(buildTypeName)!!.groupValues[1]
        }

        fun assertAllSplitsArePresent(functionalTests: List<FunctionalTest>) {
            val projectNames = functionalTests.map { find(it.name) }.toSet()
            val expectedProjectNames = largeSubprojects.flatMap { bucket ->
                (1..bucket.total).map {
                    if (it == 1) {
                        bucket.subproject.name
                    } else {
                        "${bucket.subproject.name}_$it"
                    }
                }
            }.toSet()
            assertEquals(expectedProjectNames, projectNames)
        }

        fun assertCorrectParameters(functionalTests: List<FunctionalTest>) {
            functionalTests.forEach {
                // e.g. core_2
                val projectNameWithSplit = find(it.name)
                // e.g. core
                val projectName = projectNameWithSplit.substringBefore('_')

                val numberOfSplits = largeSubprojects.find { it.subproject.name == projectName }!!.total
                val runnerStep = it.steps.items.find { it.name == "GRADLE_RUNNER" } as GradleBuildStep
                if (projectNameWithSplit.contains("_")) {
                    val split = projectNameWithSplit.substringAfter('_')
                    assertTrue(runnerStep.tasks!!.startsWith("clean $projectName:") && runnerStep.gradleParams!!.contains("-PtestSplit=$split/$numberOfSplits"))
                } else {
                    assertTrue(runnerStep.tasks!!.startsWith("clean $projectName:") && runnerStep.gradleParams!!.contains("-PtestSplit=1/$numberOfSplits"))
                }
            }
        }

        fun assertLargeProjectsAreSplittedCorrectly(id: String) {
            val functionalTestsWithSplit = rootProject.searchSubproject(id).functionalTests.filter(FunctionalTest::isLargeProjectSplit)

            assertAllSplitsArePresent(functionalTestsWithSplit)
            assertCorrectParameters(functionalTestsWithSplit)
        }

        assertLargeProjectsAreSplittedCorrectly("Gradle_Check_Stage_QuickFeedbackLinuxOnly")
        assertLargeProjectsAreSplittedCorrectly("Gradle_Check_Stage_QuickFeedback")
        assertLargeProjectsAreSplittedCorrectly("Gradle_Check_Stage_ReadyforMerge")
        assertLargeProjectsAreSplittedCorrectly("Gradle_Check_Stage_ReadyforNightly")
        assertLargeProjectsAreSplittedCorrectly("Gradle_Check_Stage_ReadyforRelease")
    }

    @Test
    fun canDeferSlowTestsToLaterStage() {
        val model = CIBuildModel()
        val rootProject = RootProject(model)
        val slowSubprojects = model.subProjects.filter { it.containsSlowTests }.map { it.name }

        fun FunctionalTest.isSlow(): Boolean = slowSubprojects.any { name.contains(it) }
        fun Project.subprojectContainsSlowTests(id: String): Boolean = searchSubproject(id).functionalTests.any(FunctionalTest::isSlow)

        assertTrue(!rootProject.subprojectContainsSlowTests("Gradle_Check_Stage_QuickFeedbackLinuxOnly"))
        assertTrue(!rootProject.subprojectContainsSlowTests("Gradle_Check_Stage_QuickFeedback"))
        assertTrue(!rootProject.subprojectContainsSlowTests("Gradle_Check_Stage_ReadyforMerge"))
        assertTrue(rootProject.subprojectContainsSlowTests("Gradle_Check_Stage_ReadyforNightly"))
        assertTrue(rootProject.subprojectContainsSlowTests("Gradle_Check_Stage_ReadyforRelease"))
    }

    @Test
    fun onlyReadyForNightlyTriggerHasUpdateBranchStatus() {
        val model = CIBuildModel()
        val rootProject = RootProject(model)
        val triggerNameToTasks = rootProject.buildTypes.map { it.uuid to ((it as StagePasses).steps.items[0] as GradleBuildStep).tasks }.toMap()
        val readyForNightlyId = toTriggerId("MasterAccept")
        assertEquals("createBuildReceipt updateBranchStatus", triggerNameToTasks[readyForNightlyId])
        val otherTaskNames = triggerNameToTasks.filterKeys { it != readyForNightlyId }.values.toSet()
        assertEquals(setOf("createBuildReceipt"), otherTaskNames)
    }

    private fun toTriggerId(id: String) = "Gradle_Check_Stage_${id}_Trigger"

    @Test
    fun allSubprojectsAreListed() {
        val knownSubProjectNames = CIBuildModel().subProjects.map { it.asDirectoryName() }
        subProjectFolderList().forEach {
            assertTrue(
                it.name in knownSubProjectNames,
                "Not defined: $it"
            )
        }
    }

    @Test
    fun uuidsAreUnique() {
        val uuidList = CIBuildModel().stages.flatMap { it.functionalTests.map { ft -> ft.uuid } }
        assertEquals(uuidList.distinct(), uuidList)
    }

    @Test
    fun testsAreCorrectlyConfiguredForAllSubProjects() {
        CIBuildModel().subProjects.filter {
            !listOf(
                "soak", // soak test
                "distributions", // build distributions
                "docs", // sanity check
                "architectureTest" // sanity check
            ).contains(it.name)
        }.forEach {
            val dir = getSubProjectFolder(it)
            assertEquals(it.unitTests, File(dir, "src/test").isDirectory, "${it.name}'s unitTests is wrong!")
            assertEquals(it.functionalTests, File(dir, "src/integTest").isDirectory, "${it.name}'s functionalTests is wrong!")
            assertEquals(it.crossVersionTests, File(dir, "src/crossVersionTest").isDirectory, "${it.name}'s crossVersionTests is wrong!")
        }
    }

    @Test
    fun allSubprojectsDefineTheirUnitTestPropertyCorrectly() {
        val projectsWithUnitTests = CIBuildModel().subProjects.filter { it.unitTests }
        val projectFoldersWithUnitTests = subProjectFolderList().filter {
            File(it, "src/test").exists() &&
                it.name != "docs" && // docs:check is part of Sanity Check
                it.name != "architecture-test" // architectureTest:test is part of Sanity Check
        }
        assertFalse(projectFoldersWithUnitTests.isEmpty())
        projectFoldersWithUnitTests.forEach {
            assertTrue(projectsWithUnitTests.map { it.asDirectoryName() }.contains(it.name), "Contains unit tests: $it")
        }
    }

    @Test
    fun allSubprojectsDefineTheirFunctionTestPropertyCorrectly() {
        val projectsWithFunctionalTests = CIBuildModel().subProjects.filter { it.functionalTests }
        val projectFoldersWithFunctionalTests = subProjectFolderList().filter {
            File(it, "src/integTest").exists() &&
                it.name != "distributions" && // distributions:integTest is part of Build Distributions
                it.name != "soak" // soak tests have their own test category
        }
        assertFalse(projectFoldersWithFunctionalTests.isEmpty())
        projectFoldersWithFunctionalTests.forEach {
            assertTrue(projectsWithFunctionalTests.map { it.asDirectoryName() }.contains(it.name), "Contains functional tests: $it")
        }
    }

    @Test
    fun allSubprojectsDefineTheirCrossVersionTestPropertyCorrectly() {
        val projectsWithCrossVersionTests = CIBuildModel().subProjects.filter { it.crossVersionTests }
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
        val testCoverage = TestCoverage(1, TestType.quickFeedbackCrossVersion, Os.windows, JvmVersion.java11, JvmVendor.oracle)
        val shortenedId = testCoverage.asConfigurationId(CIBuildModel(), "veryLongSubprojectNameLongerThanEverythingWeHave")
        assertTrue(shortenedId.length < 80)
        assertEquals("Gradle_Check_QckFdbckCrssVrsn_1_vryLngSbprjctNmLngrThnEvrythngWHv", shortenedId)

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_iIntegT", testCoverage.asConfigurationId(CIBuildModel(), "internalIntegTesting"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_buildCache", testCoverage.asConfigurationId(CIBuildModel(), "buildCache"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_1_0", testCoverage.asConfigurationId(CIBuildModel()))
    }

    @Test
    fun canDeactivateBuildCacheForSpecificStage() {
        val m = CIBuildModel(
            projectPrefix = "Gradle_BuildCacheDeactivatedForStage_",
            stages = listOf(
                Stage(StageNames.WINDOWS_10_EVALUATION_QUICK,
                    trigger = Trigger.never,
                    runsIndependent = true,
                    functionalTests = listOf(
                        TestCoverage(20, TestType.quick, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor))),
                Stage(StageNames.WINDOWS_10_EVALUATION_PLATFORM,
                    trigger = Trigger.never,
                    runsIndependent = true,
                    disablesBuildCache = true,
                    functionalTests = listOf(
                        TestCoverage(21, TestType.platform, Os.windows, JvmCategory.MAX_VERSION.version, vendor = JvmCategory.MAX_VERSION.vendor)))
            )
        )
        val p = RootProject(m)
        assertTrue(p.subProjects.size == 2)

        val buildStepsWithCache = (p.subProjectsOrder[0] as StageProject)
            .subProjects[0].buildTypes.map { ((it as FunctionalTest).steps.items[0] as GradleBuildStep) }
        buildStepsWithCache.forEach {
            assertTrue(it.gradleParams!!.contains("--build-cache"))
        }

        val buildStepsWithoutCache = (p.subProjectsOrder[1] as StageProject)
            .subProjects[0].buildTypes.map { ((it as FunctionalTest).steps.items[0] as GradleBuildStep) }
        buildStepsWithoutCache.forEach {
            assertFalse(it.gradleParams!!.contains("--build-cache"))
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

    private fun subProjectFolderList(): List<File> {
        val subprojectFolders = File("../subprojects").listFiles().filter { it.isDirectory }
        assertFalse(subprojectFolders.isEmpty())
        return subprojectFolders
    }

    private fun getSubProjectFolder(subProject: GradleSubproject): File = File("../subprojects/${subProject.asDirectoryName()}")

    private fun printTree(project: Project, indent: String = "") {
        println(indent + project.id + " (Project)")
        project.buildTypes.forEach { bt ->
            println("$indent+- ${bt.id} (Config)")
        }
        project.subProjects.forEach { subProject ->
            printTree(subProject, "$indent   ")
        }
    }
}
