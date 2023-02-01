package projects

import common.failedTestArtifactDestination
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import model.CIBuildModel
import model.FunctionalTestBucketProvider
import model.Stage
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
        param("teamcity.ui.settings.readOnly", "true")
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
    }

    var prevStage: Stage? = null
    model.stages.forEach { stage ->
        val stageProject = StageProject(model, functionalTestBucketProvider, performanceTestBucketProvider, stage, id!!.value)
        val stagePasses = StagePasses(model, stage, prevStage, stageProject)
        buildType(stagePasses)
        subProject(stageProject)
        prevStage = stage
    }

    buildTypesOrder = buildTypes
    subProjectsOrder = subProjects

    cleanup {
        baseRule {
            history(days = 14)
        }
        baseRule {
            artifacts(
                days = 14, artifactPatterns = """
                +:**/*
                +:$failedTestArtifactDestination/**/*"
            """.trimIndent()
            )
        }
    }
})
