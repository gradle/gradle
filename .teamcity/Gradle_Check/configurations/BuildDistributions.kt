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
})
