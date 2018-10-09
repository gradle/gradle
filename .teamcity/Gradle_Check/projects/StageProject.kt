package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
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

    val specificBuildTypes = stage.specificBuilds.map {
        buildType(it.create(model))
    }

    stage.performanceTests.forEach {
        buildType(PerformanceTest(model, it))
    }

    stage.functionalTests.forEach { testCoverage ->
        val isSplitIntoBuckets = testCoverage.testType != TestType.soak
        if (isSplitIntoBuckets) {
            val functionalTests = subProject(FunctionalTestProject(model, testCoverage))
            if (stage.functionalTestsDependOnSpecificBuilds) {
                functionalTests.buildTypes.forEach { functionalTestBuildType ->
                    functionalTestBuildType.dependencies {
                        specificBuildTypes.forEach { specificBuildType ->
                            dependency(specificBuildType) {
                                snapshot {
                                    onDependencyFailure = FailureAction.CANCEL
                                    onDependencyCancel = FailureAction.CANCEL
                                }
                            }
                        }
                    }

                }
            }
        } else {
            buildType(FunctionalTest(model, testCoverage))
        }
    }
})
