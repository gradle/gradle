package configurations

import configurations.CompileAll.BuildCacheType.PRODUCTION_BUILD_CACHE
import model.CIBuildModel
import model.Stage

class CompileAll(model: CIBuildModel, stage: Stage, buildCacheType: BuildCacheType = PRODUCTION_BUILD_CACHE) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model, buildCacheType))
    name = "Compile All ${buildCacheType.nameSuffix}".trim()
    description = "Compiles all production/test source code and warms up the build cache"

    features {
        publishBuildStatusToGithub(model)
    }

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=true ${buildCacheType.additionalParameters}",
        extraParameters = buildScanTag("CompileAll") + " " + "-Porg.gradle.java.installations.auto-download=false"
    )

    artifactRules = """$artifactRules
        subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel, buildCacheType: BuildCacheType = PRODUCTION_BUILD_CACHE) =
            buildTypeId(model.projectId, buildCacheType)
        fun buildTypeId(projectId: String, buildCacheType: BuildCacheType = PRODUCTION_BUILD_CACHE) =
            "${projectId}_CompileAllBuild${buildCacheType.buildTypeIdSuffix}"
    }

    enum class BuildCacheType {
        PRODUCTION_BUILD_CACHE,
        BUILD_CACHE_NG {
            override val nameSuffix: String
                get() = "BuildCacheNG"
            override val buildTypeIdSuffix: String
                get() = "_BuildCacheNG"
            override val additionalParameters: String
                get() = "-Dorg.gradle.unsafe.cache.ng=true"
        }
        ;

        open val nameSuffix: String
            get() = ""
        open val buildTypeIdSuffix: String
            get() = ""
        open val additionalParameters: String
            get() = ""
    }
}
