package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.TestType

class StageProject(model: CIBuildModel, stage: Stage) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.name.replace(" ", "").replace("-","")}"
    this.extId = uuid
    this.name = stage.name
    this.description = stage.description

    features {
        if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
            buildReportTab("API Compatibility Report", "report-distributions-binary-compatibility-report.html")
        }
        if (!stage.performanceTests.isEmpty()) {
            buildReportTab("Performance", "report-performance-performance-tests.zip!report/index.html")
            buildReportTab("Performance Failures", "report-performance-performance-tests.zip!scenario-report.html")
        }
    }

    stage.specificBuilds.forEach {
        buildType(it.create(model))
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
