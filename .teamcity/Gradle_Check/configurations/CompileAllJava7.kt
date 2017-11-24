package configurations

import model.CIBuildModel
import model.JvmVendor
import model.JvmVersion
import model.OS

class CompileAllJava7(model: CIBuildModel) : BaseGradleBuildType(model, {
    uuid = "${model.projectPrefix}CompileAllJava7"
    extId = uuid
    name = "Compile All Java 7"
    description = "Compiles all sources on Java 7 to populate the build cache"

    val os = OS.linux

    params {
        param("env.JAVA_HOME", "%$os.${JvmVersion.java7}.${JvmVendor.oracle}.64bit%")
    }

    if (model.publishStatusToGitHub) {
        features {
            publishBuildStatusToGithub()
        }
    }

    applyDefaults(model, this, "compileAll", os = os, extraParameters = "-DenableCodeQuality=true")
}, usesParentBuildCache = true)
