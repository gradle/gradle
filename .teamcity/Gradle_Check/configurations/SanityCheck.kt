package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

class SanityCheck(model: CIBuildModel) : BuildType({
    uuid = "${model.projectPrefix}SanityCheck"
    extId = uuid
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    applyDefaults(model, this, "compileAll sanityCheck", extraParameters = "-DenableCodeQuality=true --parallel")
})
