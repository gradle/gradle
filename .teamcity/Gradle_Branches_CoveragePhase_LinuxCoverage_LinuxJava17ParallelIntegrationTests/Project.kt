package Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests

import Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "bfa3729a-1e69-4ccf-96ca-29c6d4188225"
    extId = "Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests"
    parentId = "Gradle_Branches_CoveragePhase_LinuxCoverage"
    name = "Linux - Java 1.7 - Parallel integration tests"
    description = "Parallel integration tests for Linux"

    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTests_)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_8)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_6)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_7)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_4)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_5)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_2)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ParallelIntegrationTest_3)
})
