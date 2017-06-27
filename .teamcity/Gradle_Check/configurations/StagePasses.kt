package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v10.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v10.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v10.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.TestType
import model.Trigger

class StagePasses(model: CIBuildModel, stageNumber: Int, stage: Stage) : BuildType({
    uuid = "${model.projectPrefix}Stage${stageNumber}_Passes"
    extId = uuid
    name = "$stageNumber Stage Passes"

    applyDefaultSettings(this)
    artifactRules = "build/build-receipt.properties"

    val triggerExcludes = """
        -:design-docs
        -:subprojects/docs/src/docs/release
    """.trimIndent()
    val masterReleaseFiler = model.masterAndReleaseBranches.joinToString(prefix = "+:", separator = "\n+:")

    if (stage.trigger == Trigger.eachCommit) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = triggerExcludes
            branchFilter = masterReleaseFiler
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
            triggerRules = triggerExcludes
            param("branchFilter", masterReleaseFiler)
        }

    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = "createBuildReceipt"
            gradleParams = "-PtimestampedVersion -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptLinux
        }
        if (model.tagBuilds) {
            gradle {
                name = "TAG_BUILD"
                executionMode = BuildStep.ExecutionMode.ALWAYS
                tasks = "tagBuild"
                buildFile = "gradle/buildTagging.gradle"
                gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
                useGradleWrapper = true
            }
        }
    }

    dependencies {
        if (stageNumber > 1) {
            dependency("${model.projectPrefix}Stage${stageNumber - 1}_Passes") {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }

        stage.specificBuilds.forEach {
            dependency(it.create(model)) {
                snapshot {}
            }
        }

        stage.performanceTests.forEach { performanceTest ->
            dependency(performanceTest.asId(model)) {
                snapshot {}
            }
        }

        stage.functionalTests.forEach { testCoverage ->
            val isSplitIntoBuckets = testCoverage.testType != TestType.soak
            if (isSplitIntoBuckets) {
                model.subProjects.forEach { subProject ->
                    dependency(testCoverage.asId(model) + "_" + subProject) {
                        snapshot {}
                    }
                }
            } else {
                dependency(testCoverage.asId(model) + "_0") {
                    snapshot {}
                }
            }
        }
    }
})
