package configurations

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.distributedPerformanceTestParameters
import common.gradleWrapper
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep.ExecutionMode
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage

class PerformanceTestCoordinator(model: CIBuildModel, type: PerformanceTestType, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = type.asUuid(model)
    id = AbsoluteId(type.asId(model))
    name = "${type.displayName} Coordinator - Linux"

    applyPerformanceTestSettings(timeout = type.timeout)

    if (type in listOf(PerformanceTestType.test, PerformanceTestType.slow)) {
        features {
            publishBuildStatusToGithub(model)
        }
    }

    params {
        param("performance.baselines", type.defaultBaselines)
    }

    fun BuildSteps.runner(runnerName: String, runnerTasks: String, extraParameters: String = "", runnerExecutionMode: ExecutionMode = ExecutionMode.DEFAULT) {
        gradleWrapper {
            name = runnerName
            tasks = ""
            executionMode = runnerExecutionMode
            gradleParams = (performanceTestCommandLine(task = runnerTasks, baselines = "%performance.baselines%", extraParameters = type.extraParameters, os = Os.LINUX) +
                    buildToolGradleParameters(isContinue = false) +
                    distributedPerformanceTestParameters(IndividualPerformanceScenarioWorkers(model, Os.LINUX).id.toString()) +
                    listOf(buildScanTag("PerformanceTest")) +
                    model.parentBuildCache.gradleParameters(Os.LINUX) +
                    extraParameters
                    ).joinToString(separator = " ")
        }
    }

    steps {
        runner("GRADLE_RUNNER", "clean :performance:distributed${type.taskId}")
        checkCleanM2()
    }

    applyDefaultDependencies(model, this, true)
})
