package configurations

import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import model.CIBuildModel
import model.Stage

class CompileAll(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(model, stage = stage, usesParentBuildCache = true, init = {
    uuid = buildTypeId(model)
    id = AbsoluteId(uuid)
    name = "Compile All"
    description = "Compiles all the source code and warms up the build cache"

    features {
        publishBuildStatusToGithub(model)
    }

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=true",
        extraParameters = buildScanTag("CompileAll") + " " + "-Porg.gradle.java.installations.auto-download=false"
    )

    artifactRules = """$artifactRules
        subprojects/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectPrefix}CompileAllBuild"
    }
}
