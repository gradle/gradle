package configurations

import common.Os
import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import model.CIBuildModel
import model.PerformanceTestType
import model.Stage

class PerformanceTest(model: CIBuildModel, type: PerformanceTestType, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = type.asId(model)
    id = AbsoluteId(uuid)
    name = "Performance ${type.name.capitalize()} Coordinator - Linux"

    applyDefaultSettings(this, timeout = type.timeout)
    artifactRules = """
        build/report-*-performance-tests.zip => .
    """.trimIndent()
    detectHangingBuilds = false

    if (type == PerformanceTestType.test) {
        features {
            publishBuildStatusToGithub()
        }
    }

    requirements {
        // TODO this can be removed once https://github.com/gradle/gradle-private/issues/1861 is closed
        doesNotContain("teamcity.agent.name", "ec2")
    }

    params {
        param("performance.baselines", type.defaultBaselines)
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", buildJavaHome)
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("TC_USERNAME", "TeamcityRestBot")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                    gradleParameters()
                    + listOf("clean distributed${type.taskId}s -x prepareSamples --baselines %performance.baselines% ${type.extraParameters} -PtimestampedVersion -Porg.gradle.performance.branchName=%teamcity.build.branch% -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=${IndividualPerformanceScenarioWorkers(model).id} -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%",
                            buildScanTag("PerformanceTest"), "-PtestJavaHome=${coordinatorPerformanceTestJavaHome}")
                            + model.parentBuildCache.gradleParameters(Os.linux)
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
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% --daemon"
            }
        }
    }

    applyDefaultDependencies(model, this, true)
})
