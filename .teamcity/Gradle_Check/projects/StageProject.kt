package projects

import configurations.FunctionalTest
import configurations.PerformanceTest
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2018_1.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_1.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_1.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.*

class StageProject(model: CIBuildModel, stage: Stage, containsDeferredTests: Boolean, rootProjectUuid: String) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.id}"
    this.id = AbsoluteId(uuid)
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
        it.create(model, stage)
    }
    specificBuildTypes.forEach { buildType(it) }

    stage.performanceTests.forEach {
        buildType(PerformanceTest(model, it, stage))
    }

    stage.functionalTests.forEach { testCoverage ->
        val isSplitIntoBuckets = testCoverage.testType != TestType.soak
        if (isSplitIntoBuckets) {
            val functionalTests = FunctionalTestProject(model, testCoverage, stage)
            subProject(functionalTests)
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
            buildType(FunctionalTest(model, testCoverage, stage = stage))
        }
    }

    if (containsDeferredTests) {
        val deferredTestsProject = Project {
            uuid = "${rootProjectUuid}_deferred_tests"
            id = AbsoluteId(uuid)
            name = "Test coverage deferred from Quick Feedback and Build Branch accept"
            model.subProjects.forEach { subProject ->
                if (subProject.containsSlowTests) {
                    FunctionalTestProject.missingTestCoverage.forEach { testConfig ->
                        if (subProject.unitTests && testConfig.testType.unitTests) {
                            buildType(FunctionalTest(model, testConfig, subProject.name, subProject.useDaemon, stage))
                        } else if (subProject.functionalTests && testConfig.testType.functionalTests) {
                            buildType(FunctionalTest(model, testConfig, subProject.name, subProject.useDaemon, stage))
                        } else if (subProject.crossVersionTests && testConfig.testType.crossVersionTests) {
                            buildType(FunctionalTest(model, testConfig, subProject.name, subProject.useDaemon, stage))
                        }
                    }
                }
            }
        }
        subProject(deferredTestsProject)
    }
})
