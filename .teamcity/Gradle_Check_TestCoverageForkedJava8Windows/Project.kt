package Gradle_Check_TestCoverageForkedJava8Windows

import Gradle_Check_TestCoverageForkedJava8Windows.buildTypes.Gradle_Check_Stage4_TestCoverageForkedJava8Windows_1
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "d7fddd14-db8f-41ed-ba9e-ce3b3cf7695e"
    extId = "Gradle_Check_TestCoverageForkedJava8Windows"
    parentId = "Gradle_Check_Stage4"
    name = "Test Coverage - Forked Java8 Windows"

    for (bucket in 1..8) {
        buildType(Gradle_Check_Stage4_TestCoverageForkedJava8Windows_1("" + bucket))
    }
})
