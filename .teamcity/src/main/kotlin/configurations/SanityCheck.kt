package configurations

import jetbrains.buildServer.configs.kotlin.BuildStep
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import model.CIBuildModel
import model.Stage

class SanityCheck(model: CIBuildModel, stage: Stage) : BaseGradleBuildType(stage = stage, init = {
    id(buildTypeId(model))
    name = "Sanity Check"
    description = "Static code analysis, checkstyle, release notes verification, etc."

    features {
        publishBuildStatusToGithub(model)
    }

    steps {
        script {
            name = "SET_JDKS"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                nix-shell .teamcity/jdk11.nix
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
        "sanityCheck",
        extraParameters = "-DenableCodeQuality=true ${buildScanTag("SanityCheck")} " + "-Porg.gradle.java.installations.auto-download=false"
    )
}) {
    companion object {
        fun buildTypeId(model: CIBuildModel) = "${model.projectId}_SanityCheck"
    }
}
