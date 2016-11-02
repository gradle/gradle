package Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests

import Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "89c6f01c-be6a-4ba8-abfd-1351bf4f14eb"
    extId = "Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests"
    parentId = "Gradle_Branches_CoveragePhase_LinuxCoverage"
    name = "Linux - Java 1.7 - Cross-version tests"
    description = "Cross-version tests for Linux"

    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_1LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_7LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_4LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_8LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_3LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_6LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_5LinuxJ)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17CrossVersionTests_2LinuxJ)

    cleanup {
        artifacts(builds = 30)
    }
})
