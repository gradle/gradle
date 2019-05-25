package projects

import configurations.FunctionalTest
import configurations.PerformanceTestCoordinator
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.CIBuildModel
import model.GradleSubproject
import model.SpecificBuild
import model.Stage
import model.TestType

class StageProject(model: CIBuildModel, stage: Stage, containsDeferredTests: Boolean, rootProjectUuid: String) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.stageName.uuid}"
    this.id = AbsoluteId("${model.projectPrefix}Stage_${stage.stageName.id}")
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description

    features {
        if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
            buildReportTab("API Compatibility Report", "report-distributions-binary-compatibility-report.html")
            buildReportTab("Incubating APIs Report", "incubation-reports/all-incubating.html")
        }
        if (!stage.performanceTests.isEmpty()) {
            buildReportTab("Performance", "report-performance-performance-tests.zip!report/index.html")
        }
    }

    val specificBuildTypes = stage.specificBuilds.map {
        it.create(model, stage)
    }
    specificBuildTypes.forEach { buildType(it) }

    stage.performanceTests.forEach {
        buildType(PerformanceTestCoordinator(model, it, stage))
    }

    stage.functionalTests.forEach { testCoverage ->
        val isSplitIntoBuckets = testCoverage.testType != TestType.soak
        if (isSplitIntoBuckets) {
            val functionalTests = FunctionalTestProject(model, testCoverage, stage)
            subProject(functionalTests)
            if (stage.functionalTestsDependOnSpecificBuilds) {
                specificBuildTypes.forEach { specificBuildType ->
                    functionalTests.addDependencyForAllBuildTypes(specificBuildType)
                }
            }
            if (!(stage.functionalTestsDependOnSpecificBuilds && stage.specificBuilds.contains(SpecificBuild.SanityCheck)) && stage.dependsOnSanityCheck) {
                functionalTests.addDependencyForAllBuildTypes(AbsoluteId(SanityCheck.buildTypeId(model)))
            }
        } else {
            buildType(FunctionalTest(model, testCoverage, stage = stage))
        }
    }

    if (containsDeferredTests) {
        val deferredTestsProject = Project {
            uuid = "${rootProjectUuid}_deferred_tests"
            id = AbsoluteId(uuid)
            name = "Test coverage deferred from Quick Feedback and Ready for Merge"
            model.subProjects
                .filter(GradleSubproject::containsSlowTests)
                .forEach { subProject ->
                    FunctionalTestProject.missingTestCoverage
                        .filter { testConfig ->
                            subProject.hasTestsOf(testConfig.testType)
                        }
                        .forEach { testConfig ->
                            buildType(FunctionalTest(model, testConfig, subProject.name, stage))
                        }
                }
        }
        subProject(deferredTestsProject)
    }
})

private fun Project.addDependencyForAllBuildTypes(dependency: IdOwner) {
    buildTypes.forEach { functionalTestBuildType ->
        functionalTestBuildType.dependencies {
            dependency(dependency) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}
