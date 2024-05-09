package configurations

import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

class CompileAll(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
    name = "Compile All"
    description = "Compiles all production/test source code and warms up the build cache"

    features {
        publishBuildStatusToGithub(model)
    }

    steps {
        script {
            name = "SET_JDKS"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                /nix/var/nix/profiles/default/bin/nix-shell .teamcity/jdk11.nix
            """.trimIndent()
        }

        script {
            name = "CHECK_JDKS"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                echo "JAVA_HOME is now: ${'$'}{JAVA_HOME}"
            """.trimIndent()
        }
    }

    applyDefaults(
        model,
        this,
        "compileAllBuild -PignoreIncomingBuildReceipt=true -DdisableLocalCache=true",
        extraParameters = buildScanTag("CompileAll") + " " + "-Porg.gradle.java.installations.auto-download=false"
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
