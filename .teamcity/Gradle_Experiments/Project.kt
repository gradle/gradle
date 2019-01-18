package Gradle_Experiments

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.Project

object Project : Project({
    uuid = "4f868a6a-9ac9-490a-b3f9-2954a8fcc6f0"
    id("Gradle_Experiments")
    parentId("Gradle")
    name = "Experiments"
    description = "For temporary experiments"
})
