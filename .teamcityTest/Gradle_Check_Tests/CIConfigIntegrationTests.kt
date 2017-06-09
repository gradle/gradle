
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
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
