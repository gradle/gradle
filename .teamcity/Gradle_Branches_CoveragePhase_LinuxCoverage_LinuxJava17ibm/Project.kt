package Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm

import Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm.buildTypes.*
import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "bf9151a8-68c6-44ef-af0f-3b05c1ac6391"
    extId = "Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm"
    parentId = "Gradle_Branches_CoveragePhase_LinuxCoverage"
    name = "Linux - Java 1.7/IBM"
    description = "Full platform coverage for IBM JDK through forked tests"

    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_2LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_8LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_1LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_4LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_5LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_3LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_6LinuxJava17ibm)
    buildType(Gradle_Branches_CoveragePhase_LinuxCoverage_LinuxJava17ibm_7LinuxJava17ibm)
})
