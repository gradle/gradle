package configurations

import common.Os
import common.buildScanTagParam
import common.getBuildScanCustomValueParam
import model.CIBuildModel
import model.Stage

class CompileAll(model: CIBuildModel, stage: Stage) : OsAwareBaseGradleBuildType(os = Os.LINUX, stage = stage, init = {
    id(buildTypeId(model))
    name = "Compile All"
    description = "Compiles all production/test source code and warms up the build cache"

    features {
        publishBuildStatusToGithub(model)
    }

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=true",
        extraParameters = listOf(
            stage.getBuildScanCustomValueParam(),
            buildScanTagParam("CompileAll"),
            "-Porg.gradle.java.installations.auto-download=false",
        ).joinToString(" ")
    )

    artifactRules = """$artifactRules
        platforms/core-runtime/base-services/build/generated-resources/build-receipt/org/gradle/build-receipt.properties
    """.trimIndent()
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = buildTypeId(model.projectId)
        fun buildTypeId(projectId: String) = "${projectId}_CompileAllBuild"
    }
}
