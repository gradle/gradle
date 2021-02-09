package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.FunctionalTestBucketProvider
import model.Stage
import model.TestCoverage

class FunctionalTestProject(
    model: CIBuildModel,
    functionalTestBucketProvider: FunctionalTestBucketProvider,
    val testConfig: TestCoverage,
    stage: Stage
) : Project({
    this.id(testConfig.asId(model))
    this.name = testConfig.asName()
}) {
    val functionalTests: List<FunctionalTest> = functionalTestBucketProvider.createFunctionalTestsFor(stage, testConfig)

    init {
        functionalTests.forEach(this::buildType)
    }
}
