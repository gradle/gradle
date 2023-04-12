package configurations

import model.CIBuildModel
import model.Stage

class CompileAllBuildCacheNG(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id("${model.projectId}_CompileAllBuild_BuildCacheNG")
    name = "Compile All BuildCacheNG"
    description = "Compiles all production/test source code and warms up the build cache NG"

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=true -Dorg.gradle.unsafe.cache.ng=true",
        extraParameters = "-Porg.gradle.java.installations.auto-download=false"
    )

    params {
        param("env.GRADLE_CACHE_REMOTE_URL", "%gradle.experimental.cache.ng.remote.url%")
    }

    artifactRules = """$artifactRules
        subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
})
