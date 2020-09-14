package projects

import Gradle_Check.model.FunctionalTestBucketProvider
import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, functionalTestBucketProvider: FunctionalTestBucketProvider, testConfig: TestCoverage, stage: Stage) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()
}) {
    val functionalTests: List<FunctionalTest> = functionalTestBucketProvider.createFunctionalTestsFor(stage, testConfig)

    init {
        functionalTests.forEach(this::buildType)
    }
}
