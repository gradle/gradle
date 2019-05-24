package configurations

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.distributedPerformanceTestParameters
import common.gradleWrapper
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep.ExecutionMode
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildSteps
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage

class PerformanceTestCoordinator(model: CIBuildModel, type: PerformanceTestType, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = type.asId(model)
    id = AbsoluteId(uuid)
    name = "Performance ${type.name.capitalize()} Coordinator - Linux"

    applyPerformanceTestSettings(timeout = type.timeout)

    if (type == PerformanceTestType.test) {
        features {
            publishBuildStatusToGithub()
        }
    }

    params {
        param("performance.baselines", type.defaultBaselines)
    }

    fun BuildSteps.runner(runnerName: String, runnerTasks: String, extraParameters: String = "", runnerExecutionMode: BuildStep.ExecutionMode = ExecutionMode.DEFAULT) {
        gradleWrapper {
            name = runnerName
            tasks = ""
            executionMode = runnerExecutionMode
            gradleParams = (performanceTestCommandLine(task = runnerTasks, baselines = "%performance.baselines%", extraParameters = type.extraParameters)
                    + buildToolGradleParameters(isContinue = false)
                    + distributedPerformanceTestParameters(IndividualPerformanceScenarioWorkers(model).id.toString())
                    + listOf(buildScanTag("PerformanceTest"))
                    + model.parentBuildCache.gradleParameters(Os.linux)
                    + extraParameters
                    ).joinToString(separator = " ")
        }
    }

    steps {
        runner("GRADLE_RUNNER", "clean distributed${type.taskId}s")
        checkCleanM2()
        if (type.hasRerunner) {
            val rerunnerParameters = listOf(
                    "-PteamCityBuildId=%teamcity.build.id%",
                    "-PonlyPreviousFailedTestClasses=true",
                    "-Dscan.tag.RERUN_TESTS")
            runner("GRADLE_RERUNNER", "tagBuild distributed${type.taskId}s", rerunnerParameters.joinToString(" "), ExecutionMode.RUN_ON_FAILURE)
        } else {
            tagBuild(model, true)
        }
    }

    applyDefaultDependencies(model, this, true)
})

