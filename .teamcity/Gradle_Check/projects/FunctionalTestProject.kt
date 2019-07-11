package projects

import configurations.FunctionalTest
import configurations.shouldBeSkipped
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig: TestCoverage, stage: Stage) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()

    model.subProjects.forEach { subProject ->
        if (shouldBeSkipped(subProject, testConfig)) {
            return@forEach
        }
        if (stage.shouldOmitSlowProject(subProject)) {
            addMissingTestCoverage(testConfig)
            return@forEach
        }

        if (subProject.hasSeparateTestBuild(testConfig.testType)) {
            buildType(FunctionalTest(model, testConfig, listOf(subProject.name), stage))
        }
    }

    val projectNamesForMergedTestsBuild = model.subProjects
        .filter { it.includeInMergedTestBuild(testConfig.testType) }
        .map {
            it.name
        }

    if (projectNamesForMergedTestsBuild.isNotEmpty()) {
        buildType(FunctionalTest(model, testConfig, projectNamesForMergedTestsBuild, stage, allUnitTestsBuildTypeName))
    }
}) {
    companion object {
        const val allUnitTestsBuildTypeName = "AllUnitTests"

        val missingTestCoverage = mutableSetOf<TestCoverage>()

        private fun addMissingTestCoverage(coverage: TestCoverage) {
            this.missingTestCoverage.add(coverage)
        }
    }
}
