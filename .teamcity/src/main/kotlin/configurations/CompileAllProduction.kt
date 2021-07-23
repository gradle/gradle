package configurations

import model.CIBuildModel
import model.Stage

class CompileAllProduction(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
    name = "Compile All Production"
    description = "Compiles all production source code and warms up the build cache"

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
        fun buildTypeId(model: CIBuildModel) = buildTypeId(model.projectId)
        fun buildTypeId(projectId: String) = "${projectId}_CompileAllBuild"
    }
}
