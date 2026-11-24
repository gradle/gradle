import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.version
import projects.GradleBuildToolRootProject

version = "2026.2"

/*

Master (projectId: Gradle_Master)
 |----- Check (projectId: Gradle_Master_Check)
 |        |---- QuickFeedbackLinuxOnly (projectId: Gradle_Master_Check_Stage_QuickFeedbackLinuxOnly)
 |        |---- QuickFeedback
 |        |---- ...
 |        |---- ReadyforRelease
 |
 |----- Promotion (projectId: Gradle_Master_Promotion)
 |        |----- Nightly Snapshot
 |        |----- ...
 |
 |----- Util
 |        |----- WarmupEc2Agent
 |
 |----- Performance
         |----- AdHocPerformanceScenarioLinux

Release (projectId: Gradle_Release)
 |----- Check (projectId: Gradle_Release_Check)
 |        |---- QuickFeedbackLinuxOnly (projectId: Gradle_Release_Check_Stage_QuickFeedbackLinuxOnly)
 |        |---- QuickFeedback
 |        |---- ...
 |        |---- ReadyforRelease
 |
 |----- Promotion (projectId: Gradle_Release_Promotion)
 |        |----- Nightly Snapshot
 |        |----- ...
 |
 |----- Util
 |        |----- WarmupEc2Agent
 |
 |----- Performance
         |----- AdHocPerformanceScenarioLinux
 */
project(GradleBuildToolRootProject(VersionedSettingsBranch.fromDslContext()))
