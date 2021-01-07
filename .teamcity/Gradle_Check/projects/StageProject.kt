package projects

import Gradle_Check.configurations.FunctionalTestsPass
import Gradle_Check.configurations.PerformanceTest
import Gradle_Check.configurations.PerformanceTestsPass
import Gradle_Check.model.FlameGraphGeneration
import Gradle_Check.model.FunctionalTestBucketProvider
import Gradle_Check.model.PerformanceTestBucketProvider
import Gradle_Check.model.PerformanceTestCoverage
import common.failedTestArtifactDestination
import configurations.FunctionalTest
import configurations.SanityCheck
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.SpecificBuild
import model.Stage
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
                buildReportTab("API Compatibility Report", "$failedTestArtifactDestination/report-architecture-test-binary-compatibility-report.html")
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

        performanceTests = stage.performanceTests.map { createPerformanceTests(model, performanceTestBucketProvider, stage, it) } +
            stage.flameGraphs.map { createFlameGraphs(model, stage, it) }

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

        functionalTests = topLevelFunctionalTests + functionalTestProjects.flatMap(FunctionalTestProject::functionalTests)
    }

    private
    fun createPerformanceTests(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage): PerformanceTestsPass {
        val performanceTestProject = AutomaticallySplitPerformanceTestProject(model, performanceTestBucketProvider, stage, performanceTestCoverage)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private fun createFlameGraphs(model: CIBuildModel, stage: Stage, flameGraphSpec: FlameGraphGeneration): PerformanceTestsPass {
        val flameGraphBuilds = flameGraphSpec.buildSpecs.mapIndexed { index, buildSpec ->
            createFlameGraphBuild(model, stage, buildSpec, index)
        }
        val performanceTestProject = ManuallySplitPerformanceTestProject(model, flameGraphSpec, flameGraphBuilds)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private
    fun createFlameGraphBuild(model: CIBuildModel, stage: Stage, flameGraphGenerationBuildSpec: FlameGraphGeneration.FlameGraphGenerationBuildSpec, bucketIndex: Int): PerformanceTest = flameGraphGenerationBuildSpec.run {
        PerformanceTest(
            model,
            stage,
            flameGraphGenerationBuildSpec,
            description = "Flame graphs with $profiler for ${performanceScenario.scenario.scenario} | ${performanceScenario.testProject} on ${os.asName()} (bucket $bucketIndex)",
            performanceSubProject = "performance",
            bucketIndex = bucketIndex,
            extraParameters = "--profiler $profiler --tests \"${performanceScenario.scenario.className}.${performanceScenario.scenario.scenario}\"",
            testProjects = listOf(performanceScenario.testProject),
            performanceTestTaskSuffix = "PerformanceAdHocTest"
        )
    }
}

private fun FunctionalTestProject.addDependencyForAllBuildTypes(dependency: IdOwner) =
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
