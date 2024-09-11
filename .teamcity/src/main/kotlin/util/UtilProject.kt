package util

import common.Arch
import common.Os
import jetbrains.buildServer.configs.kotlin.Project

object UtilProject : Project({
    id("Util")
    name = "Util"

    buildType(RerunFlakyTest(Os.LINUX))
    buildType(RerunFlakyTest(Os.WINDOWS))
    buildType(RerunFlakyTest(Os.MACOS, Arch.AMD64))
    buildType(RerunFlakyTest(Os.MACOS, Arch.AARCH64))
    buildType(WarmupEc2Agent)

    buildType(PublishKotlinDslPlugin)

    params {
        param("env.DEVELOCITY_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
})
