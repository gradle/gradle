package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

class SanityCheck(model: CIBuildModel) : BuildType({
    uuid = "${model.projectPrefix}SanityCheck"
    extId = uuid
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    applyDefaults(model, this, "compileAll sanityCheck", extraParameters = "-DenableCodeQuality=true")
})
