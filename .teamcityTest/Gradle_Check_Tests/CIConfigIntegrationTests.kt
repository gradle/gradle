import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.JvmVersion
import model.OS
import model.SpecificBuild
import model.Stage
import model.TestCoverage
import model.TestType
import org.junit.Test
import projects.RootProject
import java.io.File
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
    fun configurationsHaveDependencies() {
        val m = CIBuildModel()
        val p = RootProject(m)
        val stagePassConfigs = p.subProjects.map { it.buildTypes[0] }
        stagePassConfigs.forEach {
            val stageNumber = stagePassConfigs.indexOf(it) + 1
            val hasPrevStage = if (stageNumber > 1) 1 else 0
            println(it.extId)
            it.dependencies.items.forEach {
                println("--> " + it.extId)
            }
            if (stageNumber <= m.stages.size) {
                val stage = m.stages[stageNumber - 1]
                var functionalTestCount = stage.functionalTests.size * m.subProjects.size
                if (stageNumber == 6) {
                    functionalTestCount -= 2 * (m.subProjects.size - 1) //Soak tests
                }
                assertEquals(
                        stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + hasPrevStage,
                        it.dependencies.items.size)
            } else {
                assertEquals(2, it.dependencies.items.size) //Individual Performance Worker
            }
        }
    }

    @Test
    fun canDeactivateBuildCacheAndAdjustCIModel() {
        val m = CIBuildModel(
                projectPrefix = "Gradle_BuildCacheDeactivated_",
                buildCacheActive = false,
                stages = listOf(
                    Stage("Sanity Check and Distribution",
                            specificBuilds = listOf(
                                    SpecificBuild.SanityCheck,
                                    SpecificBuild.BuildDistributions)),
                    Stage("Test Embedded Java8 Linux",
                            functionalTests = listOf(
                                    TestCoverage(TestType.quick, OS.linux, JvmVersion.java8))),
                    Stage("Test Embedded Java7 Windows",
                            functionalTests = listOf(
                                    TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)))
                )
        )
        val p = RootProject(m)
        printTree(p)
        assertTrue(p.subProjects.size == 3)
    }

    @Test
    fun allSubprojectsAreListed() {
        val m = CIBuildModel()
        val subprojectsFromFolders = File("../subprojects").list().map { it.replace(Regex("-([a-z\\d])"), { it.groups[1]!!.value.toUpperCase()}) }.filter {
            !it.startsWith(".") && !m.subProjectsWithoutTests.contains(it)
        }
        assertFalse(subprojectsFromFolders.isEmpty())
        subprojectsFromFolders.forEach { assertTrue(m.subProjects.contains(it), "Not defined: $it") }
    }

    private fun printTree(project: Project, indent: String = "") {
        println(indent + project.extId + " (Project)")
        project.buildTypes.forEach { bt ->
            println("$indent+- ${bt.extId} (Config)")
        }
        project.subProjects.forEach { subProject ->
            printTree(subProject, "$indent   ")
        }
    }
}
