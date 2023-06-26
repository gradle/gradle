import common.VersionedSettingsBranch
import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version
import projects.GradleBuildToolRootProject

version = "2023.05"

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
