package util

import common.Arch
import common.Os
import configurations.DownloadGitRepoToEc2Agent
import jetbrains.buildServer.configs.kotlin.Project

object UtilProject : Project({
    id("Util")
    name = "Util"

    buildType(RerunFlakyTest(Os.LINUX))
    buildType(RerunFlakyTest(Os.WINDOWS))
    buildType(RerunFlakyTest(Os.MACOS, Arch.AMD64))
    buildType(RerunFlakyTest(Os.MACOS, Arch.AARCH64))
    buildType(WarmupEc2Agent)
    buildType(DownloadGitRepoToEc2Agent)
    buildType(UpdateWrapper)

    buildType(PublishKotlinDslPlugin)

    params {
        param("env.DEVELOCITY_ACCESS_KEY", "%ge.gradle.org.access.key%")
    }
})
