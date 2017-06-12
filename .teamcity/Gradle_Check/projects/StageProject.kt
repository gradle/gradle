package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.Stage
import model.TestType
import kotlin.reflect.primaryConstructor

class StageProject(model: CIBuildModel, number: Int, stage: Stage) : Project({
    this.uuid = "${model.projectPrefix}Stage$number"
    this.extId = uuid
    this.name = "Stage $number"
    this.description = stage.description

    buildType(StagePasses(model, number, stage))

    stage.specificBuilds.forEach {
        buildType(it.primaryConstructor!!.call(model))
    }

    stage.performanceTests.forEach {
        buildType(PerformanceTest(model, it))
    }

    stage.functionalTests.forEach { testCoverage ->
        val isSplitIntoBuckets = testCoverage.testType != TestType.soak
        if (isSplitIntoBuckets) {
            subProject(FunctionalTestProject(model, testCoverage))
        } else {
            buildType(FunctionalTest(model, testCoverage))
        }
    }
})
