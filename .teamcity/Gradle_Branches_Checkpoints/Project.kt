package Gradle_Branches_Checkpoints

import Gradle_Branches_Checkpoints.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project
import jetbrains.buildServer.configs.kotlin.v10.ProjectFeature
import jetbrains.buildServer.configs.kotlin.v10.ProjectFeature.*

object Project : Project({
    uuid = "7dbb0aca-4710-4761-8a05-58f7a2a6c7f7"
    extId = "Gradle_Branches_Checkpoints"
    parentId = "Gradle_Branches"
    name = "Checkpoints"
    description = "Configurations of the stages to getting a distribution that passes QA"

    buildType(Gradle_Branches_Checkpoints_Stage3LinuxWindowsBasicCoverageBasicCompatibility)
    buildType(Gradle_Branches_Checkpoints_Stage5Branch)
    buildType(Gradle_Branches_Checkpoints_Stage4FullCoverage)
    buildType(Gradle_Branches_Checkpoints_Stage5Full)
    buildType(Gradle_Branches_Checkpoints_Stage0Foundation)
    buildType(Gradle_Branches_Checkpoints_Stage2LinuxBasicCoverage)
    buildType(Gradle_Branches_Checkpoints_Stage4BranchCoverage)

    params {
        param("env.JAVA_HOME", "%linux.java7.oracle.64bit%")
        param("system.locks.readLock.linux1ExclusiveUse", "")
    }

    features {
        feature {
            id = "PROJECT_EXT_58"
            type = "buildtype-graphs"
            param("defaultFilters", "")
            param("hideFilters", "")
            param("series", """[
  {
    "type": "valueType",
    "title": "Build Duration (all stages)",
    "key": "BuildDuration"
  }
]""")
            param("seriesTitle", "Serie")
            param("title", "New chart title")
        }
        feature {
            id = "PROJECT_EXT_59"
            type = "buildtype-graphs.order"
            param("order", "PROJECT_EXT_58")
        }
    }
})
