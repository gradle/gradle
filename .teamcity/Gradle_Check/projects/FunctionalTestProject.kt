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

    model.buildTypeBuckets.forEach { bucket ->
        if (bucket.shouldBeSkipped(testConfig) || !bucket.hasTestsOf(testConfig.testType)) {
            return@forEach
        }
        if (bucket.shouldBeSkippedInStage(stage)) {
            addMissingTestCoverage(testConfig)
            return@forEach
        }

        for (buildTypeBucket in bucket.forTestType(testConfig.testType)) {
            buildType(FunctionalTest(model, testConfig, buildTypeBucket.getSubprojectNames(), stage, buildTypeBucket.name, buildTypeBucket.extraParameters()))
        }
    }
}) {
    companion object {
        val missingTestCoverage = mutableSetOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}

