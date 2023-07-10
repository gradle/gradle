package configurations

import model.CIBuildModel
import model.Stage

class CompileAllBuildCacheNG(model: CIBuildModel, stage: Stage, oldCacheWithNgRemote: Boolean = false) : BaseGradleBuildType(stage = stage, failStage = false, init = {
    if (oldCacheWithNgRemote) {
        id("${model.projectId}_CompileAllBuild_NGRemote")
        name = "Compile All With NG Remote"
        description = "Compiles all production/test source code using old build cache but NG remote"
    } else {
        id("${model.projectId}_CompileAllBuild_BuildCacheNG")
        name = "Compile All BuildCacheNG"
        description = "Compiles all production/test source code and warms up the build cache NG"
    }

    val cacheNgEnabled = !oldCacheWithNgRemote
    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=false -Dorg.gradle.unsafe.cache.ng=$cacheNgEnabled",
        extraParameters = "-Porg.gradle.java.installations.auto-download=false"
    )

    params {
        param("env.GRADLE_CACHE_REMOTE_URL", "%gradle.experimental.cache.ng.remote.url%")
    }

    artifactRules = """$artifactRules
        subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
})
