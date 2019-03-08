
import common.JvmVendor
import common.JvmVersion
import common.NoBuildCache
import common.Os
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.GradleSubproject
import model.SpecificBuild
import model.Stage
import model.StageName
import model.StageNames
import model.TestCoverage
import model.TestType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import projects.RootProject
import java.io.File

class CIConfigIntegrationTests {
    @Test
    fun configurationTreeCanBeGenerated() {
        val m = CIBuildModel()
        val p = RootProject(m)
        printTree(p)
        assertEquals(p.subProjects.size, m.stages.size + 1)
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
                    m.subProjects.forEach { subProject ->
                        if (subProject.containsSlowTests && stage.omitsSlowProjects) {
                            return@forEach
                        }
                        if (shouldBeSkipped(subProject, testCoverage)) {
                            return@forEach
                        }
                        if (subProject.unitTests && testCoverage.testType.unitTests) {
                            functionalTestCount++
                        } else if (subProject.functionalTests && testCoverage.testType.functionalTests) {
                            functionalTestCount++
                        } else if (subProject.crossVersionTests && testCoverage.testType.crossVersionTests) {
                            functionalTestCount++
                        }
                    }
                    if (testCoverage.testType == TestType.soak) {
                        functionalTestCount++
                    }
                }

                // hacky way to consider deferred tests
                val deferredTestCount = if (stage.stageName == StageNames.READY_FOR_NIGHTLY) 10 else 0
                assertEquals(
               stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + (if (prevStage != null) 1 else 0) + deferredTestCount,
                       it.dependencies.items.size, stage.stageName.stageName)
            } else {
                assertEquals(2, it.dependencies.items.size) //Individual Performance Worker
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
                                    TestCoverage(TestType.quick, Os.linux, JvmVersion.java8),
                                    TestCoverage(TestType.quick, Os.windows, JvmVersion.java11, vendor = JvmVendor.openjdk)),
                            omitsSlowProjects = true)
                )
        )
        val p = RootProject(m)
        printTree(p)
        assertTrue(p.subProjects.size == 1)
    }

    @Test
    fun canDeferSlowTestsToLaterStage() {

        data class DefaultStageName(override val stageName: String, override val description: String) : StageName


        val m = CIBuildModel(
            projectPrefix = "",
            parentBuildCache = NoBuildCache,
            childBuildCache = NoBuildCache,
            stages = listOf(
                Stage(DefaultStageName("Stage1", "Stage1 description"),
                    functionalTests = listOf(
                        TestCoverage(TestType.quick, Os.linux, JvmVersion.java8),
                        TestCoverage(TestType.quick, Os.windows, JvmVersion.java8)),
                    omitsSlowProjects = true),
                Stage(DefaultStageName("Stage2", "Stage2 description"),
                    functionalTests = listOf(
                        TestCoverage(TestType.noDaemon, Os.linux, JvmVersion.java8),
                        TestCoverage(TestType.noDaemon, Os.windows, JvmVersion.java8)),
                    omitsSlowProjects = true),
                Stage(DefaultStageName("Stage3", "Stage3 description"),
                    functionalTests = listOf(
                        TestCoverage(TestType.platform, Os.linux, JvmVersion.java8),
                       TestCoverage(TestType.platform, Os.windows, JvmVersion.java8)),
                    omitsSlowProjects = false),
                Stage(DefaultStageName("Stage4", "Stage4 description"),
                    functionalTests = listOf(
                        TestCoverage(TestType.parallel, Os.linux, JvmVersion.java8),
                        TestCoverage(TestType.parallel, Os.windows, JvmVersion.java8)),
                    omitsSlowProjects = false)
            ),
            subProjects = listOf(
                GradleSubproject("fastBuild"),
                GradleSubproject("slowBuild", containsSlowTests = true)
            )
        )
        val p = RootProject(m)
        assertTrue(!p.hasSubProject("Stage1", "deferred"))
        assertTrue(!p.hasSubProject("Stage2", "deferred"))
        assertTrue( p.hasSubProject("Stage3", "deferred"))
        assertTrue(!p.hasSubProject("Stage4", "deferred"))
        assertTrue(p.findSubProject("Stage3", "deferred")!!.hasBuildType("Quick", "slowBuild"))
        assertTrue(p.findSubProject("Stage3", "deferred")!!.hasBuildType("NoDaemon", "slowBuild"))
    }

    private fun Project.hasSubProject(vararg patterns: String): Boolean {
        return findSubProject(*patterns) != null
    }

    private fun Project.findSubProject(vararg patterns: String): Project? {
        val tail = patterns.drop(1).toTypedArray()
        val sub =  this.subProjects.find { it.name.contains(patterns[0]) }
        return if (sub == null || tail.isEmpty()) sub else sub.findSubProject(*tail)
    }

    private fun Project.hasBuildType(vararg patterns: String): Boolean {
        return this.buildTypes.find { buildType ->
            patterns.all { pattern ->
                buildType.name.contains(pattern)
            }
        } != null
    }

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
    fun allSubprojectsDefineTheirUnitTestPropertyCorrectly() {
        val projectsWithUnitTests = CIBuildModel().subProjects.filter { it.unitTests }
        val projectFoldersWithUnitTests = subProjectFolderList().filter { File(it, "src/test").exists()
                    && it.name != "docs" //docs:check is part of Sanity Check
                    && it.name != "architecture-test" //architectureTest:test is part of Sanity Check
        }
        assertFalse(projectFoldersWithUnitTests.isEmpty())
        projectFoldersWithUnitTests.forEach {
            assertTrue(projectsWithUnitTests.map { it.asDirectoryName() }.contains(it.name), "Contains unit tests: $it")
        }
    }

    @Test
    fun allSubprojectsDefineTheirFunctionTestPropertyCorrectly() {
        val projectsWithFunctionalTests = CIBuildModel().subProjects.filter { it.functionalTests }
        val projectFoldersWithFunctionalTests = subProjectFolderList().filter { File(it, "src/integTest").exists()
                    && it.name != "distributions" //distributions:integTest is part of Build Distributions
                    && it.name != "soak"          //soak tests have their own test category
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
            assertFalse(containsSrcFileWithString(File(it, "src/integTest"), "CrossVersion", listOf("package org.gradle.testkit" ,"CrossVersionPerformanceTest")))
        }
    }

    @Test
    fun long_ids_are_shortened() {
        val testCoverage = TestCoverage(TestType.quickFeedbackCrossVersion, Os.windows, JvmVersion.java11, JvmVendor.oracle)
        val shortenedId = testCoverage.asConfigurationId(CIBuildModel(), "veryLongSubprojectNameLongerThanEverythingWeHave")
        assertTrue(shortenedId.length < 80)
        assertEquals(shortenedId, "Gradle_Check_QckFdbckCrssVrsn_Jv11_Orcl_Wndws_vryLngSbprjctNmLngrThnEvrythngWHv")

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_Java11_Oracle_Windows_iIntegT", testCoverage.asConfigurationId(CIBuildModel(), "internalIntegTesting"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_Java11_Oracle_Windows_buildCache", testCoverage.asConfigurationId(CIBuildModel(), "buildCache"))

        assertEquals("Gradle_Check_QuickFeedbackCrossVersion_Java11_Oracle_Windows_0", testCoverage.asConfigurationId(CIBuildModel()))
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

    private fun subProjectFolderList() : List<File> {
        val subprojectFolders = File("../subprojects").listFiles().filter { it.isDirectory }
        assertFalse(subprojectFolders.isEmpty())
        return subprojectFolders
    }

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
