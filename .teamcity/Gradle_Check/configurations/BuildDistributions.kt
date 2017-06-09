package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildType
import model.CIBuildModel

object BuildDistributions : BuildType({
    uuid = "${CIBuildModel.projectPrefix}BuildDistributions"
    extId = uuid
    name = "Build Distributions"
    description = "Creation and verification of the distribution and documentation"

    applyDefaults(this, "packageBuild")

    artifactRules += """
        build/distributions/*.zip => distributions
        build/build-receipt.properties
    """.trimIndent()
})
