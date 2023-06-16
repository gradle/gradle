package configurations

import common.VersionedSettingsBranch
import common.applyDefaultSettings
import common.toCapitalized
import jetbrains.buildServer.configs.kotlin.v2019_2.Dependencies
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import jetbrains.buildServer.configs.kotlin.v2019_2.SnapshotDependency
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.schedule
import jetbrains.buildServer.configs.kotlin.v2019_2.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.StageName
import model.Trigger
import projects.StageProject

class StagePasses(model: CIBuildModel, stage: Stage, prevStage: Stage?, stageProject: StageProject) : BaseGradleBuildType(init = {
    id(stageTriggerId(model, stage))
    uuid = stageTriggerUuid(model, stage)
    name = stage.stageName.stageName + " (Trigger)"
    type = Type.COMPOSITE

    applyDefaultSettings()

    features {
        publishBuildStatusToGithub(model)
    }

    val enableTriggers = model.branch.enableVcsTriggers
    if (stage.trigger == Trigger.eachCommit) {
        triggers.vcs {
            quietPeriodMode = VcsTrigger.QuietPeriodMode.USE_CUSTOM
            quietPeriod = 90
            triggerRules = triggerExcludes
            branchFilter = model.branch.branchFilter()
            enabled = enableTriggers
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
            branchFilter = model.branch.branchFilter()
            enabled = enableTriggers
        }
    }

    dependencies {
        if (!stage.runsIndependent && prevStage != null) {
            dependency(RelativeId(stageTriggerId(model, prevStage))) {
                snapshot {
                    onDependencyFailure = FailureAction.FAIL_TO_START
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
            }
        }

        snapshotDependencies(stageProject.specificBuildTypes)
        snapshotDependencies(stageProject.performanceTests)
        snapshotDependencies(stageProject.functionalTests)
        snapshotDependencies(stageProject.docsTestTriggers)
    }
})

fun stageTriggerId(model: CIBuildModel, stage: Stage) = stageTriggerId(model, stage.stageName)

fun stageTriggerUuid(model: CIBuildModel, stage: Stage) = stageTriggerUuid(model, stage.stageName)

fun stageTriggerId(model: CIBuildModel, stageName: StageName) = "${model.projectId}_Stage_${stageName.id}_Trigger"

fun stageTriggerUuid(model: CIBuildModel, stageName: StageName) = "${VersionedSettingsBranch.fromDslContext().branchName.toCapitalized()}_${model.projectId}_Stage_${stageName.uuid}_Trigger"

fun <T : BaseGradleBuildType> Dependencies.snapshotDependencies(buildTypes: Iterable<T>, snapshotConfig: SnapshotDependency.(T) -> Unit = {}) {
    buildTypes.forEach { buildType ->
        dependency(buildType.id!!) {
            snapshot {
                if (!buildType.failStage) {
                    onDependencyFailure = FailureAction.IGNORE
                    onDependencyCancel = FailureAction.IGNORE
                } else {
                    onDependencyFailure = FailureAction.ADD_PROBLEM
                    onDependencyCancel = FailureAction.FAIL_TO_START
                }
                snapshotConfig(buildType)
            }
        }
    }
}
