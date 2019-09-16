package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig: TestCoverage, stage: Stage) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()

}) {
    val functionalTests = model.buildTypeBuckets.flatMap { bucket ->
        if (bucket.shouldBeSkipped(testConfig) || !bucket.hasTestsOf(testConfig.testType)) {
            return@flatMap emptyList<FunctionalTest>()
        }
        if (bucket.shouldBeSkippedInStage(stage)) {
            addMissingTestCoverage(testConfig)
            return@flatMap emptyList<FunctionalTest>()
        }

        bucket.forTestType(testConfig.testType).map {
            buildTypeBucket -> FunctionalTest(model, testConfig, buildTypeBucket.getSubprojectNames(), stage, buildTypeBucket.name, buildTypeBucket.extraParameters())
        }
    }

    init {
        functionalTests.forEach(this::buildType)
    }

    companion object {
        val missingTestCoverage = mutableSetOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}
