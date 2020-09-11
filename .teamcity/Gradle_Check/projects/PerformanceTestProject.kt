package projects

import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import common.Os
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage

class PerformanceTestProject(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, os: Os, stage: Stage) : Project({
    val performanceTestCoverage = PerformanceTestCoverage(stage.id, os)
    this.uuid = performanceTestCoverage.asConfigurationId(model)
    this.id = AbsoluteId(uuid)
    this.name = performanceTestCoverage.asName()
}) {
    val performanceTests: List<PerformanceTest> = performanceTestBucketProvider.createPerformanceTestsFor(stage, os)

    init {
        performanceTests.forEach(this::buildType)
    }
}
