package configurations

import model.CIBuildModel
import model.Stage

class DependenciesCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, {
    uuid = "${model.projectPrefix}DependenciesCheck"
    id = uuid
    name = "Dependencies Check - Java8 Linux"
    description = "Checks external dependencies in Gradle distribution for known, published vulnerabilities"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    applyDefaults(
            model,
            this,
            "dependencyCheckAnalyze",
            notQuick = true,
            extraParameters = buildScanTag("DependenciesCheck")
    )
}, stage = stage)
