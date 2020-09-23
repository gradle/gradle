package projects

import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage

class PerformanceTestProject(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, val performanceTestCoverage: PerformanceTestCoverage) : Project({
    this.uuid = performanceTestCoverage.asConfigurationId(model, stage)
    this.id = AbsoluteId(uuid)
    this.name = performanceTestCoverage.asName()
}) {
    val performanceTests: List<PerformanceTest> = performanceTestBucketProvider.createPerformanceTestsFor(stage, performanceTestCoverage)

    init {
        performanceTests.forEach(this::buildType)
    }
}
