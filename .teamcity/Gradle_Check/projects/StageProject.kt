package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.Stage
import model.TestType

class StageProject(number: Int, stage: Stage) : Project({
    this.uuid = "${CIBuildModel.projectPrefix}Stage$number"
    this.extId = uuid
    this.name = "Stage $number"
    this.description = stage.description

    buildType(StagePasses(number, stage))

    stage.specificBuilds.forEach {
        buildType(it)
    }

    stage.performanceTests.forEach {
        buildType(PerformanceTest(it))
    }

    stage.functionalTests.forEach { testCoverage ->
        val isSplitIntoBuckets = testCoverage.testType != TestType.soak
        if (isSplitIntoBuckets) {
            subProject(FunctionalTestProject(testCoverage))
        } else {
            buildType(FunctionalTest(testCoverage))
        }
    }
})
