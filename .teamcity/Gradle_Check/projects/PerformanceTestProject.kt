package projects

import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import Gradle_Check.model.PerformanceTestProjectSpec
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.Stage

abstract class PerformanceTestProject(model: CIBuildModel, val spec: PerformanceTestProjectSpec, val performanceTests: List<PerformanceTest>) : Project({
    this.uuid = spec.asConfigurationId(model)
    this.id = AbsoluteId(uuid)
    this.name = spec.asName()
}) {
    init {
        performanceTests.forEach(this::buildType)
    }
}

class AutomaticallySplitPerformanceTestProject(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage) : PerformanceTestProject(model, performanceTestCoverage, performanceTestBucketProvider.createPerformanceTestsFor(stage, performanceTestCoverage))

class ManuallySplitPerformanceTestProject(model: CIBuildModel, projectSpec: PerformanceTestProjectSpec, performanceTests: List<PerformanceTest>) :
    PerformanceTestProject(model, projectSpec, performanceTests)
