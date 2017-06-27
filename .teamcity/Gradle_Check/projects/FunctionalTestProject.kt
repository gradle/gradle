package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig : TestCoverage) : Project({
    this.uuid = testConfig.asId(model)
    this.extId = uuid
    this.name = testConfig.asName()

    model.subProjects.forEach { subProject ->
        buildType(FunctionalTest(model, testConfig, subProject))
    }
})
