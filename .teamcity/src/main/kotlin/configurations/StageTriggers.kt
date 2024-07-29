package configurations

import common.Os
import common.applyDefaultSettings
import common.toCapitalized
import common.uuidPrefix
import jetbrains.buildServer.configs.kotlin.Dependencies
import jetbrains.buildServer.configs.kotlin.DslContext
import jetbrains.buildServer.configs.kotlin.FailureAction
import jetbrains.buildServer.configs.kotlin.RelativeId
import jetbrains.buildServer.configs.kotlin.SnapshotDependency
import jetbrains.buildServer.configs.kotlin.triggers.ScheduleTrigger
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.triggers.schedule
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import model.CIBuildModel
import model.Stage
import model.StageName
import model.Trigger
import projects.StageProject

val stageWithOsTriggers: Map<StageName, List<Os>> = mapOf(
    StageName.PULL_REQUEST_FEEDBACK to listOf(Os.LINUX, Os.WINDOWS),
    StageName.READY_FOR_NIGHTLY to listOf(Os.LINUX, Os.WINDOWS, Os.MACOS),
    StageName.READY_FOR_RELEASE to listOf(Os.LINUX, Os.WINDOWS, Os.MACOS),
)

class StageTriggers(model: CIBuildModel, stage: Stage, prevStage: Stage?, stageProject: StageProject) {
    val triggers: List<BaseGradleBuildType>

    init {
        triggers = mutableListOf()
        val allDependencies =
            stageProject.specificBuildTypes + stageProject.performanceTests + stageProject.functionalTests + stageProject.docsTestTriggers
        triggers.add(StageTrigger(model, stage, prevStage, null, allDependencies))

        stageWithOsTriggers.getOrDefault(stage.stageName, emptyList()).forEach { targetOs ->
            val dependencies = allDependencies.filter { it.os == targetOs }
            triggers.add(StageTrigger(model, stage, prevStage, targetOs, dependencies))
        }
    }
}

class StageTrigger(
    model: CIBuildModel,
    stage: Stage,
    prevStage: Stage?,
    os: Os?,
    dependencies: List<BaseGradleBuildType>
) : BaseGradleBuildType(init = {
    id(stageTriggerId(model, stage, os))
    uuid = stageTriggerUuid(model, stage, os)
    name = stage.stageName.stageName + " (Trigger)" + (os?.asName()?.toCapitalized()?.let { "($it)" } ?: "")
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
            dependOnPreviousStageTrigger(model, prevStage, os)
        }

        snapshotDependencies(dependencies)
    }
})

fun Dependencies.dependOnPreviousStageTrigger(model: CIBuildModel, prevStage: Stage, os: Os? = null) {
    if (os == null || stageWithOsTriggers.getOrDefault(prevStage.stageName, emptyList()).contains(os)) {
        dependency(RelativeId(stageTriggerId(model, prevStage, os))) {
            snapshot {
                onDependencyFailure = FailureAction.FAIL_TO_START
                onDependencyCancel = FailureAction.FAIL_TO_START
            }
        }
    }
}

fun stageTriggerId(model: CIBuildModel, stage: Stage, os: Os? = null) = stageTriggerId(model, stage.stageName, os)

fun stageTriggerUuid(model: CIBuildModel, stage: Stage, os: Os? = null) = stageTriggerUuid(model, stage.stageName, os)

fun stageTriggerId(model: CIBuildModel, stageName: StageName, os: Os? = null) =
    "${model.projectId}_Stage_${stageName.id}_${osSuffix(os)}Trigger"

fun stageTriggerUuid(model: CIBuildModel, stageName: StageName, os: Os? = null) =
    "${DslContext.uuidPrefix}_${model.projectId}_Stage_${stageName.uuid}_${osSuffix(os)}Trigger"

fun osSuffix(os: Os?) = os?.asName()?.plus("_") ?: ""

fun <T : BaseGradleBuildType> Dependencies.snapshotDependencies(
    buildTypes: Iterable<T>,
    snapshotConfig: SnapshotDependency.(T) -> Unit = {}
) {
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
