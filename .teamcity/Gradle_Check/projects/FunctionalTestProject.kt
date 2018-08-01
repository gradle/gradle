package projects

import configurations.FunctionalTest
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_1.Project
import model.*

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

        configureBuildType(model, testConfig, stage, subProject)
    }
}){
    companion object {
        val missingTestCoverage = mutableListOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}

fun Project.configureBuildType(model: CIBuildModel, testConfig: TestCoverage, stage: Stage, subProject: GradleSubproject) {
    val useDaemon = subProject.useDaemon && testConfig.testType != TestType.noDaemon

    if (subProject.unitTests && testConfig.testType.unitTests) {
        buildType(FunctionalTest(model, testConfig, subProject.name, useDaemon, stage))
    } else if (subProject.functionalTests && testConfig.testType.functionalTests) {
        buildType(FunctionalTest(model, testConfig, subProject.name, useDaemon, stage))
    } else if (subProject.crossVersionTests && testConfig.testType.crossVersionTests) {
        buildType(FunctionalTest(model, testConfig, subProject.name, useDaemon, stage))
    }
}
