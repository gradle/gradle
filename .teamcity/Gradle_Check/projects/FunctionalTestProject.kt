package projects

import configurations.FunctionalTest
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig : TestCoverage) : Project({
    this.uuid = testConfig.asId(model)
    this.extId = uuid
    this.name = testConfig.asName()

    model.subProjects.forEach { subProject ->
        if (shouldBeSkipped(subProject, testConfig)) {
            return@forEach
        }
        if (subProject.unitTests && testConfig.testType.unitTests) {
            buildType(FunctionalTest(model, testConfig, subProject.name))
        } else if (subProject.functionalTests && testConfig.testType.functionalTests) {
            buildType(FunctionalTest(model, testConfig, subProject.name))
        } else if (subProject.crossVersionTests && testConfig.testType.crossVersionTests) {
            buildType(FunctionalTest(model, testConfig, subProject.name))
        }
    }
})
