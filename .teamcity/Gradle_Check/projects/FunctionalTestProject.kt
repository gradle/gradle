package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.Stage
import model.TestCoverage

class FunctionalTestProject(model: CIBuildModel, testConfig: TestCoverage, stage: Stage, deferredFunctionalTests: MutableList<(Stage) -> List<FunctionalTest>>) : Project({
    this.uuid = testConfig.asId(model)
    this.id = AbsoluteId(uuid)
    this.name = testConfig.asName()
}) {
    val functionalTests: List<FunctionalTest>

    init {
        val (deferredTests, currentTests) = model.buildTypeBuckets
            .filter { !it.shouldBeSkipped(testConfig) && it.hasTestsOf(testConfig.testType) }
            .partition { it.shouldBeSkippedInStage(stage) }
        functionalTests = currentTests.flatMap { it.createFunctionalTestsFor(model, stage, testConfig) }
        deferredFunctionalTests.addAll(deferredTests.map { { stage: Stage -> it.createFunctionalTestsFor(model, stage, testConfig) } })
        functionalTests.forEach(this::buildType)
    }
}
