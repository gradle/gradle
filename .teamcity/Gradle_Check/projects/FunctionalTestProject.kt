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
        // TODO: Hacky. We should really be running all the subprojects on macOS
        // But we're restricting this to just a subset of projects for now
        // since we only have a small pool of macOS agents
        if (testConfig.os.subset.isNotEmpty() && !testConfig.os.subset.contains(subProject.name)) {
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
