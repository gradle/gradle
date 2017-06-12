
import configurations.BuildDistributions
import configurations.SanityCheck
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.JvmVersion
import model.OS
import model.Stage
import model.TestCoverage
import model.TestType
import org.junit.Test
import projects.RootProject
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CIConfigIntegrationTests {
    @Test
    fun configurationTreeCanBeGenerated() {
        printTree(RootProject)
        assertTrue(RootProject.subProjects.size == CIBuildModel.stages.size)
    }

    @Test
    fun configurationsHaveDependencies() {
        val stagePassConfigs = RootProject.subProjects.map { it.buildTypes[0] }
        stagePassConfigs.forEach {
            val stageNumber = stagePassConfigs.indexOf(it) + 1
            val hasPrevStage = if (stageNumber > 1) 1 else 0
            val stage = CIBuildModel.stages[stageNumber - 1]
            println(it.extId)
            it.dependencies.items.forEach {
                println("--> " + it.extId)
            }
            var functionalTestCount = stage.functionalTests.size * CIBuildModel.testBuckets.size
            if (stageNumber == 6) {
                functionalTestCount -= 2 * (CIBuildModel.testBuckets.size - 1) //Soak tests
            }
            assertEquals(
                    stage.specificBuilds.size + functionalTestCount + stage.performanceTests.size + hasPrevStage,
                    it.dependencies.items.size)
        }
    }

    @Test
    fun canDeactivateBuildCacheAndAdjustCIModel() {
        CIBuildModel.projectPrefix = "Gradle_BuildCacheDeactivated_"
        CIBuildModel.buildCacheActive = false
        CIBuildModel.stages = listOf(
                Stage("Sanity Check and Distribution",
                        specificBuilds = listOf(
                                SanityCheck,
                                BuildDistributions)),
                Stage("Test Embedded Java8 Linux",
                        functionalTests = listOf(
                                TestCoverage(TestType.quick, OS.linux, JvmVersion.java8))),
                Stage("Test Embedded Java7 Windows",
                        functionalTests = listOf(
                                TestCoverage(TestType.quick, OS.windows, JvmVersion.java7)))
        )
        printTree(RootProject)
        assertTrue(RootProject.subProjects.size == 3)
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
