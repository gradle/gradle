package Gradle_Check_TestCoverageNoDaemonJava8Linux

import Gradle_Check_TestCoverageNoDaemonJava8Linux.buildTypes.Gradle_Check_TestCoverageNoDaemonJava8Linux_1
import jetbrains.buildServer.configs.kotlin.v10.Project

object Project : Project({
    uuid = "51bad452-af42-4a32-ac1d-2f45caacf63f"
    extId = "Gradle_Check_TestCoverageNoDaemonJava8Linux"
    parentId = "Gradle_Check_Stage7"
    name = "Test Coverage - No-daemon Java8 Linux"


    for (bucket in 1..8) {
        buildType(Gradle_Check_TestCoverageNoDaemonJava8Linux_1("" + bucket))
    }
})
