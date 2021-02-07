package util

import jetbrains.buildServer.configs.kotlin.v2019_2.Project

object UtilProject : Project({
    id("Util")
    name = "Util"

    buildType(WarmupEc2Agent)
})
