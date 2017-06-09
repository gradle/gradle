package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

object SanityCheck : BuildType({
    uuid = "${CIBuildModel.projectPrefix}SanityCheck"
    extId = uuid
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    applyDefaults(this, "compileAll sanityCheck", extraParameters = "-DenableCodeQuality=true")
})
