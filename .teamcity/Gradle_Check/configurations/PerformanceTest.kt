package configurations

import jetbrains.buildServer.configs.kotlin.v2017_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2017_2.buildSteps.script
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage

class PerformanceTest(model: CIBuildModel, type: PerformanceTestType, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = type.asId(model)
    id = uuid
    name = "Performance ${type.name.capitalize()} Coordinator - Linux"

    applyDefaultSettings(this, timeout = type.timeout)
    detectHangingBuilds = false
    maxRunningBuilds = 2

    if (type == PerformanceTestType.test) {
        features {
            publishBuildStatusToGithub()
        }
    }

    params {
        param("performance.baselines", type.defaultBaselines)
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("TC_USERNAME", "TeamcityRestBot")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                    listOf("clean distributed${type.taskId}s -x prepareSamples --baselines %performance.baselines% ${type.extraParameters} -PtimestampedVersion -Porg.gradle.performance.branchName=%teamcity.build.branch% -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=${IndividualPerformanceScenarioWorkers(model).id} -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%",
                            buildScanTag("PerformanceTest"))
                            + gradleParameters
                            + model.parentBuildCache.gradleParameters()
                    ).joinToString(separator = " ")
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptUnixLike
        }
        if (model.tagBuilds) {
            gradleWrapper {
                name = "TAG_BUILD"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "tagBuild"
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% -Djava7Home=%linux.jdk.for.gradle.compile%"
                buildFile = "gradle/buildTagging.gradle"
            }
        }
    }

    applyDefaultDependencies(model, this, true)
})
