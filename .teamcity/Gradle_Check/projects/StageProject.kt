package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v10.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.TestType

class StageProject(model: CIBuildModel, number: Int, stage: Stage) : Project({
    this.uuid = "${model.projectPrefix}Stage$number"
    this.extId = uuid
    this.name = "Stage $number"
    this.description = stage.description

    buildType(StagePasses(model, number, stage))

    features {
        if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
            feature {
                type = "ReportTab"
                param("startPage", "report-distributions-binary-compatibility-report.html")
                param("title", "API Compatibility Report")
                param("type", "BuildReportTab")
            }
        }
        if (!stage.performanceTests.isEmpty()) {
            feature {
                type = "ReportTab"
                param("startPage", "report-performance-performance-tests.zip!report/index.html")
                param("title", "Performance")
                param("type", "BuildReportTab")
            }
            feature {
                type = "ReportTab"
                param("startPage", "report-performance-performance-tests.zip!scenario-report.html")
                param("title", "Performance Failures")
                param("type", "BuildReportTab")
            }
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
