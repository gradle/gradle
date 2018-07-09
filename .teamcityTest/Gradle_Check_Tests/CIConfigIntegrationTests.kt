
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v2018_1.Project
import model.*
import org.junit.Test
import projects.RootProject
import java.io.File
import java.util.regex.Pattern
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val releaseAccept = p.subProjects.find { it.name.contains("Release Accept") }!!
        val macOS = releaseAccept.subProjects.find { it.name.contains("Macos") }!!

        macOS.buildTypes.forEach { buildType ->
            assertFalse(OS.macos.ignoredSubprojects.any { subproject ->
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
                var deferredTestCount = if (stage.name.contains("Master Accept")) 10 else 0
                assertEquals(
               stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + (if (prevStage != null) 1 else 0) + deferredTestCount,
                       it.dependencies.items.size, "${stage.name}")
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
                        Stage("Quick Feedback", "Runs all checks and functional tests with an embedded test executer",
                            specificBuilds = listOf(
                                    SpecificBuild.SanityCheck,
                                    SpecificBuild.BuildDistributions),
                            functionalTests = listOf(
                                    TestCoverage(TestType.quick, OS.linux, JvmVersion.java8),
                                    TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)),
                            omitsSlowProjects = true)
                )
        )
        val p = RootProject(m)
        printTree(p)
        assertTrue(p.subProjects.size == 1)
    }

    @Test
    fun canDeferSlowTestsToLaterStage() {
        val ft = listOf(TestCoverage(TestType.quick, OS.linux, JvmVersion.java8), TestCoverage(TestType.quick, OS.windows, JvmVersion.java7))
        val m = CIBuildModel(
            projectPrefix = "",
            parentBuildCache = NoBuildCache,
            childBuildCache = NoBuildCache,
            stages = listOf(
                Stage("Stage1", "Stage1 description",
                    functionalTests = listOf(
                        TestCoverage(TestType.quick, OS.linux, JvmVersion.java7),
                        TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)),
                    omitsSlowProjects = true),
                Stage("Stage2", "Stage2 description",
                    functionalTests = listOf(
                        TestCoverage(TestType.noDaemon, OS.linux, JvmVersion.java7),
                        TestCoverage(TestType.noDaemon, OS.windows, JvmVersion.java7)),
                    omitsSlowProjects = true),
                Stage("Stage3", "Stage3 description",
                    functionalTests = listOf(
                        TestCoverage(TestType.platform, OS.linux, JvmVersion.java7),
                       TestCoverage(TestType.platform, OS.windows, JvmVersion.java7)),
                    omitsSlowProjects = false),
                Stage("Stage4", "Stage4 description",
                    functionalTests = listOf(
                        TestCoverage(TestType.parallel, OS.linux, JvmVersion.java7),
                        TestCoverage(TestType.parallel, OS.windows, JvmVersion.java7)),
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
        val m = CIBuildModel()
        subProjectFolderList().forEach {
            assertTrue(m.subProjects.map { it.asDirectoryName() }.contains(it.name), "Not defined: $it")
        }
    }

    @Test
    fun allSubprojectsDefineTheirUnitTestPropertyCorrectly() {
        val projectsWithUnitTests = CIBuildModel().subProjects.filter { it.unitTests }
        val projectFoldersWithUnitTests = subProjectFolderList().filter { File(it, "src/test").exists() &&
                    it.name != "docs" //docs:check is part of Sanity Check
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
