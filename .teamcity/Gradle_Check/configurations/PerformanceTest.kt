package configurations

import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.distributedPerformanceTestParameters
import common.gradleWrapper
import common.performanceTestCommandLine
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage

class PerformanceTest(model: CIBuildModel, type: PerformanceTestType, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
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

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                    buildToolGradleParameters(isContinue = false)
                        + performanceTestCommandLine(task = "distributed${type.taskId}s", baselines = "%performance.baselines%", extraParameters = type.extraParameters)
                        + distributedPerformanceTestParameters(IndividualPerformanceScenarioWorkers(model).id.toString())
                        + listOf(buildScanTag("PerformanceTest"))
                        + model.parentBuildCache.gradleParameters(Os.linux)
                    ).joinToString(separator = " ")
        }
        checkCleanM2()
        tagBuild(model, true)
    }

    applyDefaultDependencies(model, this, true)
})
