package projects

import Gradle_Check.configurations.FunctionalTestsPass
import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.configurations.PerformanceTestsPass
import Gradle_Check.model.FunctionalTestBucketProvider
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import Gradle_Check.model.Scenario
import Gradle_Check.model.prepareScenariosStep
import common.Os
import configurations.FunctionalTest
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.PerformanceTestType
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.TestType

class StageProject(model: CIBuildModel, functionalTestBucketProvider: FunctionalTestBucketProvider, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, rootProjectUuid: String) : Project({
    this.uuid = "${model.projectPrefix}Stage_${stage.stageName.uuid}"
    this.id = AbsoluteId("${model.projectPrefix}Stage_${stage.stageName.id}")
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description
}) {
    val specificBuildTypes: List<BuildType>

    val performanceTests: List<PerformanceTestsPass>

    val functionalTests: List<FunctionalTest>

    init {
        features {
            if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
                buildReportTab("API Compatibility Report", "report-architecture-test-binary-compatibility-report.html")
                buildReportTab("Incubating APIs Report", "incubation-reports/all-incubating.html")
            }
            if (stage.performanceTests.isNotEmpty()) {
                buildReportTab("Performance", "performance-test-results.zip!report/index.html")
            }
        }

        specificBuildTypes = stage.specificBuilds.map {
            it.create(model, stage)
        }
        specificBuildTypes.forEach(this::buildType)

        val performanceTestsFromModel = stage.performanceTests.map { createPerformanceTests(model, performanceTestBucketProvider, stage, it) }

        performanceTests = if (stage.stageName == StageNames.EXPERIMENTAL_PERFORMANCE) {
            val coverage = PerformanceTestCoverage(14, PerformanceTestType.adHoc, Os.LINUX, numberOfBuckets = 1, withoutDependencies = true)
            val performanceTests = Os.values().mapIndexed { index, os -> index to os }.flatMap { (index, os) ->
                val osCoverage = PerformanceTestCoverage(14, PerformanceTestType.adHoc, os, numberOfBuckets = 1, withoutDependencies = true)
                listOf("async-profiler", "async-profiler-alloc").mapIndexed { profIndex, profiler ->
                    createProfiledPerformanceTest(
                        model,
                        stage,
                        osCoverage,
                        testProject = "santaTrackerAndroidBuild",
                        scenario = Scenario("org.gradle.performance.regression.corefeature.FileSystemWatchingPerformanceTest", "assemble for non-abi change with file system watching"),
                        profiler = profiler,
                        bucketIndex = 2 * index + profIndex
                    )
                }
            }
            val performanceTestProject = ManuallySplitPerformanceTestProject(model, coverage, performanceTests)
            subProject(performanceTestProject)
            performanceTestsFromModel + listOf(PerformanceTestsPass(model, performanceTestProject).also(this::buildType))
        } else {
            performanceTestsFromModel
        }

        val (topLevelCoverage, allCoverage) = stage.functionalTests.partition { it.testType == TestType.soak || it.testDistribution }
        val topLevelFunctionalTests = topLevelCoverage
            .map { FunctionalTest(model, it.asConfigurationId(model), it.asName(), it.asName(), it, stage = stage) }
        topLevelFunctionalTests.forEach(this::buildType)

        val functionalTestProjects = allCoverage
            .map { testCoverage ->
                val functionalTestProject = FunctionalTestProject(model, functionalTestBucketProvider, testCoverage, stage)
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

        functionalTestProjects.forEach { functionalTestProject ->
            this@StageProject.subProject(functionalTestProject)
            this@StageProject.buildType(FunctionalTestsPass(model, functionalTestProject))
        }

        val deferredTestsForThisStage = functionalTestBucketProvider.createDeferredFunctionalTestsFor(stage)
        if (deferredTestsForThisStage.isNotEmpty()) {
            val deferredTestsProject = Project {
                uuid = "${rootProjectUuid}_deferred_tests"
                id = AbsoluteId(uuid)
                name = "Test coverage deferred from Quick Feedback and Ready for Merge"
                deferredTestsForThisStage.forEach(this::buildType)
            }
            subProject(deferredTestsProject)
        }

        functionalTests = topLevelFunctionalTests + functionalTestProjects.flatMap(FunctionalTestProject::functionalTests) + deferredTestsForThisStage
    }

    private
    fun createPerformanceTests(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage): PerformanceTestsPass {
        val performanceTestProject = AutomaticallySplitPerformanceTestProject(model, performanceTestBucketProvider, stage, performanceTestCoverage)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private
    fun createProfiledPerformanceTest(model: CIBuildModel, stage: Stage, performanceTestCoverage: PerformanceTestCoverage, testProject: String, scenario: Scenario, profiler: String, bucketIndex: Int): PerformanceTest = PerformanceTest(
        model,
        stage,
        performanceTestCoverage,
        description = "Flame graphs with $profiler for ${scenario.scenario} | $testProject on ${performanceTestCoverage.os.asName()}",
        performanceSubProject = "performance",
        bucketIndex = bucketIndex,
        extraParameters = "--profiler $profiler",
        testProjects = listOf(testProject),
        preBuildSteps = prepareScenariosStep(
            testProject,
            listOf(scenario),
            performanceTestCoverage.os
        )
    )
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
