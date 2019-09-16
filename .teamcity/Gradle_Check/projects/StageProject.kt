package projects

import configurations.FunctionalTest
import configurations.PerformanceTestCoordinator
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import model.BuildTypeBucket
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
import model.TestType

class StageProject(model: CIBuildModel, stage: Stage, containsDeferredTests: Boolean, rootProjectUuid: String) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.stageName.uuid}"
    this.id = AbsoluteId("${model.projectPrefix}Stage_${stage.stageName.id}")
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description
}) {
    val specificBuildTypes: List<BuildType>

    val performanceTests: List<PerformanceTestCoordinator>

    val functionalTests: List<FunctionalTest>

    init {
        features {
            if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
                buildReportTab("API Compatibility Report", "report-distributions-binary-compatibility-report.html")
                buildReportTab("Incubating APIs Report", "incubation-reports/all-incubating.html")
            }
            if (!stage.performanceTests.isEmpty()) {
                buildReportTab("Performance", "report-performance-performance-tests.zip!report/index.html")
            }
        }

        specificBuildTypes = stage.specificBuilds.map {
            it.create(model, stage)
        }
        specificBuildTypes.forEach { buildType(it) }

        performanceTests = stage.performanceTests.map { PerformanceTestCoordinator(model, it, stage) }
        performanceTests.forEach(this::buildType)

        val topLevelFunctionalTests = stage.functionalTests
            .filter { it.testType == TestType.soak }
            .map { FunctionalTest(model, it, stage = stage) }
        topLevelFunctionalTests.forEach(this::buildType)

        val functionalTestProjects = stage.functionalTests
            .filter { it.testType != TestType.soak }
            .map { testCoverage ->
                val functionalTestProject = FunctionalTestProject(model, testCoverage, stage)
                if (stage.functionalTestsDependOnSpecificBuilds) {
                    specificBuildTypes.forEach { specificBuildType ->
                        functionalTestProject.addDependencyForAllBuildTypes(specificBuildType)
                    }
                }
                if (!(stage.functionalTestsDependOnSpecificBuilds && stage.specificBuilds.contains(SpecificBuild.SanityCheck)) && stage.dependsOnSanityCheck) {
                    functionalTestProject.addDependencyForAllBuildTypes(AbsoluteId(SanityCheck.buildTypeId(model)))
                }
                functionalTestProject
            }

        functionalTestProjects.forEach {
            subProject(it)
        }

        functionalTests = topLevelFunctionalTests + functionalTestProjects.flatMap(FunctionalTestProject::functionalTests)

        if (containsDeferredTests) {
            val deferredTestsProject = Project {
                uuid = "${rootProjectUuid}_deferred_tests"
                id = AbsoluteId(uuid)
                name = "Test coverage deferred from Quick Feedback and Ready for Merge"
                model.buildTypeBuckets
                    .filter(BuildTypeBucket::containsSlowTests)
                    .forEach { bucket ->
                        FunctionalTestProject.missingTestCoverage
                            .filter { testConfig ->
                                bucket.hasTestsOf(testConfig.testType)
                            }
                            .forEach { testConfig ->
                                bucket.forTestType(testConfig.testType).forEach {
                                    buildType(FunctionalTest(model, testConfig, it.getSubprojectNames(), stage, it.name))
                                }
                            }
                    }
            }
            subProject(deferredTestsProject)
        }
    }
}

private fun FunctionalTestProject.addDependencyForAllBuildTypes(dependency: IdOwner) {
    functionalTests.forEach { functionalTestBuildType ->
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
