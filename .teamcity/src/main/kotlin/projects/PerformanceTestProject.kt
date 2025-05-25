package projects

import configurations.PerformanceTest
import jetbrains.buildServer.configs.kotlin.Project
import model.CIBuildModel
import model.PerformanceTestBucketProvider
import model.PerformanceTestCoverage
import model.PerformanceTestProjectSpec
import model.Stage

abstract class PerformanceTestProject(
    model: CIBuildModel,
    val spec: PerformanceTestProjectSpec,
    val performanceTests: List<PerformanceTest>,
) : Project({
        this.id(spec.asConfigurationId(model))
        this.name = spec.asName()
    }) {
    init {
        performanceTests.forEach(this::buildType)
    }
}

class AutomaticallySplitPerformanceTestProject(
    model: CIBuildModel,
    performanceTestBucketProvider: PerformanceTestBucketProvider,
    stage: Stage,
    performanceTestCoverage: PerformanceTestCoverage,
) : PerformanceTestProject(
        model,
        performanceTestCoverage,
        performanceTestBucketProvider.createPerformanceTestsFor(stage, performanceTestCoverage),
    )

class ManuallySplitPerformanceTestProject(
    model: CIBuildModel,
    projectSpec: PerformanceTestProjectSpec,
    performanceTests: List<PerformanceTest>,
) : PerformanceTestProject(model, projectSpec, performanceTests)
