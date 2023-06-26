package projects

import common.cleanupRule
import common.hiddenArtifactDestination
import common.isSecurityFork
import configurations.PerformanceTestsPass
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.FunctionalTestBucketProvider
import model.Stage
import model.StageName
import model.StatisticsBasedPerformanceTestBucketProvider
import java.io.File

class CheckProject(
    model: CIBuildModel,
    functionalTestBucketProvider: FunctionalTestBucketProvider
) : Project({
    id("Check")
    name = "Check"
    val performanceTestBucketProvider = StatisticsBasedPerformanceTestBucketProvider(model, File("performance-test-durations.json"), File("performance-tests-ci.json"))

    params {
        param("credentialsStorageType", "credentialsJSON")
        // Disallow Web UI changes to TeamCity settings
        param("teamcity.ui.settings.readOnly", "true")
        // Avoid rebuilding same revision if it's already built on another branch (pre-tested commit)
        param("teamcity.vcsTrigger.runBuildOnSameRevisionInEveryBranch", "false")
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%;%ge-td-dogfooding.grdev.net.access.key%")

        text(
            "additional.gradle.parameters",
            "",
            display = ParameterDisplay.NORMAL,
            allowEmpty = true,
            description = "The extra gradle parameters you want to pass to this build, e.g. `-PrerunAllTests` or `--no-build-cache`"
        )
        text(
            "reverse.dep.*.additional.gradle.parameters",
            "",
            display = ParameterDisplay.NORMAL,
            allowEmpty = true,
            description = "The extra gradle parameters you want to pass to all dependencies of this build, e.g. `-PrerunAllTests` or `--no-build-cache`"
        )
        text(
            "reverse.dep.*.skip.build",
            "",
            display = ParameterDisplay.NORMAL,
            allowEmpty = true,
            description = "Set to 'true' if you want to skip all dependency builds"
        )
    }

    var prevStage: Stage? = null
    val previousPerformanceTestPasses: MutableList<PerformanceTestsPass> = mutableListOf()
    model.stages.forEach { stage ->
        if (isSecurityFork() && stage.stageName > StageName.READY_FOR_RELEASE) {
            return@forEach
        }
        val stageProject = StageProject(model, functionalTestBucketProvider, performanceTestBucketProvider, stage, previousPerformanceTestPasses)
        val stagePasses = StagePasses(model, stage, prevStage, stageProject)
        buildType(stagePasses)
        subProject(stageProject)

        prevStage = stage
        previousPerformanceTestPasses.addAll(stageProject.performanceTests)
    }

    buildTypesOrder = buildTypes
    subProjectsOrder = subProjects

    cleanupRule(
        historyDays = 14,
        artifactsDays = 7,
        artifactsPatterns = """
                +:**/*
                +:$hiddenArtifactDestination/**/*"
        """.trimIndent()
    )
})
