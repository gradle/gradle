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
    val testCoverage: TestCoverage,
    stage: Stage
) : Project({
    this.id(testCoverage.asId(model))
    this.name = testCoverage.asName()
}) {
    val functionalTests: List<FunctionalTest> = functionalTestBucketProvider.createFunctionalTestsFor(stage, testCoverage)

    init {
        functionalTests.forEach(this::buildType)
    }
}
