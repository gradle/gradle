import jetbrains.buildServer.configs.kotlin.v2019_2.project
import jetbrains.buildServer.configs.kotlin.v2019_2.version
import jetbrains.buildServer.configs.kotlin.v2019_2.DslContext
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import common.Branch
import projects.GradleBuildToolRootProject

version = "2020.2"

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
val branch = Branch.current()
val parentProjectId = "Gradle"

DslContext.parentProjectId = AbsoluteId(parentProjectId)
DslContext.projectId = AbsoluteId("${parentProjectId}_${branch.name}")
DslContext.projectName = branch.name

project(GradleBuildToolRootProject(branch))
