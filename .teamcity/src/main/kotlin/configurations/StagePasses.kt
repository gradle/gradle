package configurations

import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.Dependencies
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.SnapshotDependency
import jetbrains.buildServer.configs.kotlin.v2019_2.buildSteps.script
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.StageName
import model.StageNames
import model.Trigger
import projects.StageProject

class StagePasses(model: CIBuildModel, stage: Stage, prevStage: Stage?, stageProject: StageProject) : BaseGradleBuildType(init = {
    id(stageTriggerId(model, stage))
    name = stage.stageName.stageName + " (Trigger)"

    applyDefaultSettings()
    artifactRules = "subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties"

    features {
        publishBuildStatusToGithub(model)
    }

    if (stage.trigger == Trigger.eachCommit) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = triggerExcludes
            branchFilter = branchFilter(model.branch)
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
            branchFilter = branchFilter(model.branch)
        }
    }

    val buildScanTags = model.buildScanTags + stage.id

    val defaultGradleParameters = (
        buildToolGradleParameters() +
            buildScanTags.map(::buildScanTag)
        ).joinToString(" ") + " -Porg.gradle.java.installations.auto-download=false"
    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ":base-services:createBuildReceipt" + if (stage.stageName == StageNames.READY_FOR_NIGHTLY) " updateBranchStatus" else ""
            gradleParams = defaultGradleParameters
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptUnixLike
        }
    }

    dependencies {
        if (!stage.runsIndependent && prevStage != null) {
            dependency(RelativeId(stageTriggerId(model, prevStage))) {
                snapshot {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                }
            }
        }

        snapshotDependencies(stageProject.specificBuildTypes)
        snapshotDependencies(stageProject.performanceTests) { performanceTestPass ->
            if (!performanceTestPass.performanceSpec.failsStage) {
                onDependencyFailure = FailureAction.IGNORE
                onDependencyCancel = FailureAction.IGNORE
            }
        }
        snapshotDependencies(stageProject.functionalTests)
    }
})

fun stageTriggerId(model: CIBuildModel, stage: Stage) = stageTriggerId(model, stage.stageName)

fun stageTriggerId(model: CIBuildModel, stageName: StageName) = "${model.projectId}_Stage_${stageName.id}_Trigger"

fun <T : BuildType> Dependencies.snapshotDependencies(buildTypes: Iterable<T>, snapshotConfig: SnapshotDependency.(T) -> Unit = {}) {
    buildTypes.forEach { buildType ->
        dependency(buildType.id!!) {
            snapshot {
                snapshotConfig(buildType)
            }
        }
    }
}
