package configurations

import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2018_2.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.TestType
import model.Trigger
import projects.FunctionalTestProject

class StagePasses(model: CIBuildModel, stage: Stage, prevStage: Stage?, containsDeferredTests: Boolean, rootProjectUuid: String) : BaseGradleBuildType(model, init = {
    uuid = stageTriggerUuid(model, stage)
    id = stageTriggerId(model, stage)
    name = stage.stageName.stageName + " (Trigger)"

    applyDefaultSettings(this)
    artifactRules = "build/build-receipt.properties"

    val triggerExcludes = """
        -:.idea
        -:.github
        -:.teamcity
        -:.teamcityTest
        -:subprojects/docs/src/docs/release
    """.trimIndent()
    val masterReleaseFilter = model.masterAndReleaseBranches.joinToString(prefix = "+:", separator = "\n+:")

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    if (stage.trigger == Trigger.eachCommit) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = triggerExcludes
            branchFilter = masterReleaseFilter
        }
    } else if (stage.trigger != Trigger.never) {
        triggers.schedule {
            if (stage.trigger == Trigger.weekly) {
                schedulingPolicy = weekly {
                    dayOfWeek = ScheduleTrigger.DAY.Saturday
                    hour = 1
                }
            } else {
                schedulingPolicy = daily {
                    hour = 0
                    minute = 30
                }
            }
            triggerBuild = always()
            withPendingChangesOnly = true
            param("revisionRule", "lastFinished")
            param("branchFilter", masterReleaseFilter)
        }

    }

    params {
        param("env.JAVA_HOME", buildJavaHome)
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = "createBuildReceipt"
            gradleParams = "-PtimestampedVersion --daemon"
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
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% ${buildScanTag("StagePasses")} --daemon"
            }
        }
    }

    dependencies {
        if (!stage.runsIndependent && prevStage != null) {
            dependency(stageTriggerId(model, prevStage)) {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }

        stage.specificBuilds.forEach {
            dependency(it.create(model, stage)) {
                snapshot {}
            }
        }

        stage.performanceTests.forEach { performanceTest ->
            dependency(AbsoluteId(performanceTest.asId(model))) {
                snapshot {}
            }
        }

        stage.functionalTests.forEach { testCoverage ->
            val isSplitIntoBuckets = testCoverage.testType != TestType.soak
            if (isSplitIntoBuckets) {
                model.subProjects.forEach { subProject ->
                    if (shouldBeSkipped(subProject, testCoverage)) {
                        return@forEach
                    }
                    if (subProject.containsSlowTests && stage.omitsSlowProjects) {
                        return@forEach
                    }
                    if (subProject.unitTests && testCoverage.testType.unitTests) {
                        dependency(AbsoluteId(testCoverage.asConfigurationId(model, subProject.name))) { snapshot {} }
                    } else if (subProject.functionalTests && testCoverage.testType.functionalTests) {
                        dependency(AbsoluteId(testCoverage.asConfigurationId(model, subProject.name))) { snapshot {} }
                    } else if (subProject.crossVersionTests && testCoverage.testType.crossVersionTests) {
                        dependency(AbsoluteId(testCoverage.asConfigurationId(model, subProject.name))) { snapshot {} }
                    }
                }
            } else {
                dependency(AbsoluteId(testCoverage.asConfigurationId(model))) {
                    snapshot {}
                }
            }
        }

        if (containsDeferredTests) {
            model.subProjects.forEach { subProject ->
                if (subProject.containsSlowTests) {
                    FunctionalTestProject.missingTestCoverage.forEach { testConfig ->
                        if (subProject.unitTests && testConfig.testType.unitTests) {
                            dependency(AbsoluteId(testConfig.asConfigurationId(model, subProject.name))) { snapshot {} }
                        } else if (subProject.functionalTests && testConfig.testType.functionalTests) {
                            dependency(AbsoluteId(testConfig.asConfigurationId(model, subProject.name))) { snapshot {} }
                        } else if (subProject.crossVersionTests && testConfig.testType.crossVersionTests) {
                            dependency(AbsoluteId(testConfig.asConfigurationId(model, subProject.name))) { snapshot {} }
                        }
                    }
                }
            }
        }
    }
})

fun stageTriggerUuid(model: CIBuildModel, stage: Stage) = "${model.projectPrefix}Stage_${stage.stageName.uuid}_Trigger"
fun stageTriggerId(model: CIBuildModel, stage: Stage) = AbsoluteId("${model.projectPrefix}Stage_${stage.stageName.id}_Trigger")
