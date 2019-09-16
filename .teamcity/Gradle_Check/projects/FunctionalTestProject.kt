package projects

import configurations.FunctionalTest
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.BuildTypeBucket
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
        fun createFunctionalTests(stage: Stage, bucket: BuildTypeBucket) = bucket.forTestType(testConfig.testType).map {
            buildTypeBucket -> FunctionalTest(model, testConfig, buildTypeBucket.getSubprojectNames(), stage, buildTypeBucket.name, buildTypeBucket.extraParameters())
        }

        val (deferredTests, currentTests) = model.buildTypeBuckets
            .filter { !it.shouldBeSkipped(testConfig) && it.hasTestsOf(testConfig.testType) }
            .partition { it.shouldBeSkippedInStage(stage) }
        functionalTests = currentTests.flatMap { createFunctionalTests(stage, it) }
        deferredFunctionalTests.addAll(deferredTests.map { bucket ->
            { stage: Stage -> createFunctionalTests(stage, bucket) }
        })
        functionalTests.forEach(this::buildType)
    }
}

