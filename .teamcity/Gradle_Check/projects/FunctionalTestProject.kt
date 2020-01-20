package projects

import Gradle_Check.model.GradleBuildBucketProvider
import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, gradleBuildBucketProvider: GradleBuildBucketProvider, testConfig: TestCoverage, stage: Stage) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()
}) {
    val functionalTests: List<FunctionalTest> = gradleBuildBucketProvider.createFunctionalTestsFor(stage, testConfig)

    init {
        functionalTests.forEach(this::buildType)
    }
}
