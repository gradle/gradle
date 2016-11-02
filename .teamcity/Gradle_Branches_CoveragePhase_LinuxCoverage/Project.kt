package Gradle_Branches_CoveragePhase_LinuxCoverage

import Gradle_Branches_CoveragePhase_LinuxCoverage.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "eda4bf0f-4ab4-44d4-87c5-2f57f0883696"
    extId = "Gradle_Branches_CoveragePhase_LinuxCoverage"
    parentId = "Gradle_Branches_CoveragePhase"
    name = "Linux Coverage"
    description = "Configurations that provide full test coverage on Linux"

    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18GradleceptionBuildingGrad)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava18SmokeTestsAgainst3rdParty)
})
