package projects

import common.hiddenArtifactDestination
import common.uuidPrefix
import configurations.BaseGradleBuildType
import configurations.DocsTestProject
import configurations.DocsTestTrigger
import configurations.FunctionalTest
import configurations.FunctionalTestsPass
import configurations.OsAwareBaseGradleBuildType
import configurations.PartialTrigger
import configurations.PerformanceTest
import configurations.PerformanceTestsPass
import configurations.SmokeTests
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.Project
import jetbrains.buildServer.configs.kotlin.RelativeId
import model.CIBuildModel
import model.FlameGraphGeneration
import model.FunctionalTestBucketProvider
import model.GRADLE_BUILD_SMOKE_TEST_NAME
import model.PerformanceTestBucketProvider
import model.PerformanceTestCoverage
import model.SpecificBuild
import model.Stage
import model.StageName
import model.TestCoverage
import model.TestType

class StageProject(
    model: CIBuildModel,
    functionalTestBucketProvider: FunctionalTestBucketProvider,
    performanceTestBucketProvider: PerformanceTestBucketProvider,
    stage: Stage,
    previousPerformanceTestPasses: List<PerformanceTestsPass>,
    previousCrossVersionTests: List<BaseGradleBuildType>
) : Project({
    this.id("${model.projectId}_Stage_${stage.stageName.id}")
    this.uuid = "${DslContext.uuidPrefix}_${model.projectId}_Stage_${stage.stageName.uuid}"
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description
}) {
    val specificBuildTypes: List<OsAwareBaseGradleBuildType>

    val performanceTests: List<PerformanceTestsPass>

    val functionalTests: List<OsAwareBaseGradleBuildType>

    val crossVersionTests: List<OsAwareBaseGradleBuildType>

    val docsTestTriggers: List<OsAwareBaseGradleBuildType>

    init {
        features {
            buildReportTab("Problems Report", "problems-report.html")
            if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
                buildReportTab("API Compatibility Report", "$hiddenArtifactDestination/report-architecture-test-binary-compatibility-report.html")
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

        val (topLevelCoverage, allCoverage) = stage.functionalTests.partition { it.testType == TestType.soak }
        val topLevelFunctionalTests = topLevelCoverage
            .map { FunctionalTest(model, it.asConfigurationId(model), it.asName(), it.asName(), it, stage = stage) }
        topLevelFunctionalTests.forEach(this::buildType)

        val functionalTestProjects = allCoverage.map { testCoverage -> FunctionalTestProject(model, functionalTestBucketProvider, testCoverage, stage) }

        functionalTestProjects.forEach { functionalTestProject ->
            this@StageProject.subProject(functionalTestProject)
        }
        val functionalTestsPass = functionalTestProjects.map { functionalTestProject ->
            FunctionalTestsPass(model, functionalTestProject).also { this@StageProject.buildType(it) }
        }

        functionalTests = topLevelFunctionalTests + functionalTestsPass
        crossVersionTests = topLevelFunctionalTests.filter { it.testCoverage.isCrossVersionTest } + functionalTestsPass.filter { it.testCoverage.isCrossVersionTest }
        if (stage.stageName !in listOf(StageName.QUICK_FEEDBACK_LINUX_ONLY, StageName.QUICK_FEEDBACK)) {
            if (topLevelFunctionalTests.size + functionalTestProjects.size > 1) {
                buildType(PartialTrigger("All Functional Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_FuncTests", model, functionalTests))
            }
            val smokeTests = specificBuildTypes.filterIsInstance<SmokeTests>()
            if (smokeTests.size > 1) {
                buildType(PartialTrigger("All Smoke Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_SmokeTests", model, smokeTests))
            }
            if (crossVersionTests.size > 1) {
                buildType(PartialTrigger("All Cross-Version Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_CrossVersionTests", model, crossVersionTests + previousCrossVersionTests))
            }

            // in gradleBuildSmokeTest, most of the tests are for using the configuration cache on gradle/gradle
            val configCacheTests = (functionalTests + specificBuildTypes).filter { it.name.lowercase().contains("configcache") || it.name.contains(GRADLE_BUILD_SMOKE_TEST_NAME) }
            if (configCacheTests.size > 1) {
                buildType(PartialTrigger("All ConfigCache Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_ConfigCacheTests", model, configCacheTests))
            }
            if (specificBuildTypes.size > 1) {
                buildType(PartialTrigger("All Specific Builds for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_SpecificBuilds", model, specificBuildTypes))
            }
            if (performanceTests.size > 1) {
                buildType(createPerformancePartialTrigger(model, stage))
            }
        }

        val docsTestProjects = stage.docsTests.map { DocsTestProject(model, stage, it.os, it.testJava, it.docsTestTypes) }
        docsTestProjects.forEach(this::subProject)
        docsTestTriggers = docsTestProjects.map { DocsTestTrigger(model, it) }
        docsTestTriggers.forEach(this::buildType)

        stage.performanceTestPartialTriggers.forEach { trigger ->
            buildType(
                PartialTrigger(
                    trigger.triggerName, trigger.triggerId, model,
                    trigger.dependencies.map { performanceTestCoverage ->
                        val targetPerformanceTestPassBuildTypeId = "${performanceTestCoverage.asConfigurationId(model)}_Trigger"
                        (performanceTests + previousPerformanceTestPasses).first { it.id.toString().endsWith(targetPerformanceTestPassBuildTypeId) }
                    }
                )
            )
        }
    }

    private
    val TestCoverage.isCrossVersionTest
        get() = testType in setOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion)

    private
    fun createPerformanceTests(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage): PerformanceTestsPass {
        val performanceTestProject = AutomaticallySplitPerformanceTestProject(model, performanceTestBucketProvider, stage, performanceTestCoverage)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private
    fun createPerformancePartialTrigger(model: CIBuildModel, stage: Stage): PartialTrigger<PerformanceTestsPass> {
        val performancePartialTrigger = PartialTrigger("All Performance Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_PerformanceTests", model, performanceTests)
        performanceTests.forEach { performanceTestTrigger ->
            // The space removal is necessary - otherwise it doesn't show
            val artifactDirName = performanceTestTrigger.name.replace(" ", "")
            performancePartialTrigger.dependencies {
                artifacts(performanceTestTrigger) {
                    id = "artifact_dependency_${performancePartialTrigger.uuid}_${(performanceTestTrigger.id as RelativeId).relativeId}"
                    artifactRules = "**/* => $artifactDirName"
                }
            }
        }
        return performancePartialTrigger
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
