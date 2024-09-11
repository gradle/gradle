import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.project
import jetbrains.buildServer.configs.kotlin.version
import projects.GradleBuildToolRootProject

version = "2024.03"

/*

Master (buildTypeId: Gradle_Master)
 |----- Check (buildTypeId: Gradle_Master_Check)
 |        |---- QuickFeedbackLinux (buildTypeId: Gradle_Master_Check_QuickFeedbackLinux)
 |        |---- QuickFeedback
 |        |---- ...
 |        |---- ReadyForRelease
 |
 |----- Promotion (buildTypeId: Gradle_Master_Promotion)
 |        |----- Nightly Snapshot
 |        |----- ...
 |
 |----- Util
         |----- WarmupEc2Agent
         |----- AdHocPerformanceTest

Release (buildTypeId: Gradle_Release)
 |----- Check (buildTypeId: Gradle_Release_Check)
 |        |---- QuickFeedbackLinux (buildTypeId: Gradle_Release_Check_QuickFeedbackLinux)
 |        |---- QuickFeedback
 |        |---- ...
 |        |---- ReadyForRelease
 |
 |----- Promotion (buildTypeId: Gradle_Release_Promotion)
 |        |----- Nightly Snapshot
 |        |----- ...
 |
 |----- Util
         |----- WarmupEc2Agent
         |----- AdHocPerformanceTest
 */
project(GradleBuildToolRootProject(VersionedSettingsBranch.fromDslContext()))
