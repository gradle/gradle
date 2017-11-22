package configurations

import model.CIBuildModel

class CompileAllJava7(model: CIBuildModel) : BaseGradleBuildType(model, {
    uuid = "${model.projectPrefix}CompileAllJava7"
    extId = uuid
    name = "Compile All Java 7"
    description = "Compiles all sources on Java 7 to populate the build cache"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
    }

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    applyDefaults(model, this, "compileAll", extraParameters = "-DenableCodeQuality=true --parallel")
}, usesParentBuildCache = true)
