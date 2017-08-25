package configurations

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script
import model.CIBuildModel

class IndividualPerformanceScenarioWorkers(model: CIBuildModel) : BuildType({
    uuid = model.projectPrefix + "IndividualPerformanceScenarioWorkersLinux"
    extId = uuid
    name = "Individual Performance Scenario Workers - Linux"

    applyDefaultSettings(this, timeout = 420)
    artifactRules = """
        subprojects/*/build/test-results-*.zip => results
        subprojects/*/build/tmp/**/log.txt => failure-logs
    """.trimIndent()

    params {
        param("baselines", "defaults")
        param("channel", "commits")
        param("checks", "all")
        param("runs", "defaults")
        param("warmups", "defaults")
        param("templates", "")
        param("scenario", "")

        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")

        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                    listOf("""cleanSamples cleanFullPerformanceTest %templates% fullPerformanceTests --scenarios "%scenario%" --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% -x prepareSamples -x performanceReport -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -Porg.gradle.performance.db.password=%performance.db.password.tcagent% -PtimestampedVersion""")
                            + gradleParameters
                            + (if (model.buildCacheActive) gradleBuildCacheParameters else emptyList())
                    ).joinToString(separator = " ")
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = m2CleanScriptLinux
        }
    }

    val buildDistributions = BuildDistributions(model)
    dependencies {
        dependency(buildDistributions) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        artifacts(buildDistributions) {
            id = "ARTIFACT_DEPENDENCY_${buildDistributions.extId}"
            cleanDestination = true
            artifactRules = """
                    distributions/*-all.zip => incoming-distributions
                    build-receipt.properties => incoming-distributions
                """.trimIndent()
        }
    }
})
