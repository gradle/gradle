package configurations

import model.CIBuildModel

class BuildDistributions(model: CIBuildModel) : BaseGradleBuildType(model, {
    uuid = "${model.projectPrefix}BuildDistributions"
    extId = uuid
    name = "Build Distributions"
    description = "Creation and verification of the distribution and documentation"

    applyDefaults(model, this, "packageBuild")

    artifactRules = """$artifactRules
        build/distributions/*.zip => distributions
        build/build-receipt.properties
    """.trimIndent()

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }
})
