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

    model.subprojectBuckets.forEach { subprojectBucket ->
        if (subprojectBucket.shouldBeSkipped(testConfig)) {
            return@forEach
        }
        if (stage.shouldOmitSlowProject(subprojectBucket)) {
            addMissingTestCoverage(testConfig)
            return@forEach
        }

        buildType(FunctionalTest(model, testConfig, subprojectBucket.subprojects.map { it.name }, stage, subprojectBucket.name, subprojectBucket.extraParameters()))
    }
}) {
    companion object {
        val missingTestCoverage = mutableSetOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}
