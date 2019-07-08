package projects

import configurations.FunctionalTest
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig: TestCoverage, stage: Stage) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()

    model.subProjects.forEach { subProject ->
        if (shouldBeSkipped(subProject, testConfig)) {
            return@forEach
        }
        if (subProject.containsSlowTests && stage.omitsSlowProjects) {
            addMissingTestCoverage(testConfig)
            return@forEach
        }

        if (subProject.hasTestsOf(testConfig.testType) && !subProject.hasOnlyUnitTests()) {
            buildType(FunctionalTest(model, testConfig, listOf(subProject.name), stage))
        }
    }

    if (testConfig.testType.unitTests && model.subProjects.any { it.hasOnlyUnitTests() }) {
        val projectsWithOnlyUnitTests = model.subProjects.filter { it.hasOnlyUnitTests() }.map { it.name }
        buildType(FunctionalTest(model, testConfig, projectsWithOnlyUnitTests, stage, allUnitTestsBuildTypeName))
    }

}){
    companion object {
        const val allUnitTestsBuildTypeName = "AllUnitTests"

        val missingTestCoverage = mutableSetOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}
