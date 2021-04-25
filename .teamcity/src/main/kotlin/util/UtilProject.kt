package util

import common.Os
import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object UtilProject : Project({
    id("Util")
    name = "Util"

    buildType(RerunFlakyTest(Os.LINUX))
    buildType(RerunFlakyTest(Os.WINDOWS))
    buildType(RerunFlakyTest(Os.MACOS))
    buildType(WarmupEc2Agent)

    params {
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
})
