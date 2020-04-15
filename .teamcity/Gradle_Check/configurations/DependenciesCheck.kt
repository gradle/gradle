package configurations

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class DependenciesCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, init = {
    uuid = "${model.projectPrefix}DependenciesCheck"
    id = AbsoluteId(uuid)
    name = "Dependencies Check - Java8 Linux"
    description = "Checks external dependencies in Gradle distribution for known, published vulnerabilities"

    params {
        param("env.JAVA_HOME", buildJavaHome())
    }

    features {
        publishBuildStatusToGithub(model)
    }

    applyDefaults(
            model,
            this,
            "dependencyCheckAnalyze",
            notQuick = true,
            extraParameters = buildScanTag("DependenciesCheck")
    )
})
