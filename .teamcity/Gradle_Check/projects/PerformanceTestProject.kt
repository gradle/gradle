package projects

import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage

abstract class PerformanceTestProject(model: CIBuildModel, val performanceTestCoverage: PerformanceTestCoverage, val performanceTests: List<PerformanceTest>) : Project({
    this.uuid = performanceTestCoverage.asConfigurationId(model)
    this.id = AbsoluteId(uuid)
    this.name = "${performanceTestCoverage.asName()}${if (performanceTestCoverage.withoutDependencies) " without dependencies" else ""}"
}) {
    init {
        performanceTests.forEach(this::buildType)
    }
}

class AutomaticallySplitPerformanceTestProject(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage) : PerformanceTestProject(model, performanceTestCoverage, performanceTestBucketProvider.createPerformanceTestsFor(stage, performanceTestCoverage))

class ManuallySplitPerformanceTestProject(model: CIBuildModel, performanceTestCoverage: PerformanceTestCoverage, performanceTests: List<PerformanceTest>) :
    PerformanceTestProject(model, performanceTestCoverage, performanceTests)
