package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux : BuildType({
    uuid = "ea0766d2-0405-468b-93a8-98c3ac8bd29e"
    extId = "Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux"
    name = "Individual performance scenario workers - Linux"

    artifactRules = """build/reports/** => reports
                            subprojects/*/build/test-results-*.zip => results"""

    params {
        param("baselines", "defaults")
        param("channel", "commits")
        param("checks", "all")
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("runs", "defaults")
        param("scenario", "")
        param("templates", "")
        param("warmups", "defaults")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            gradleParams = """cleanSamples cleanFullPerformanceTest %templates% fullPerformanceTests --scenarios "%scenario%" --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% -x prepareSamples -x performanceReport -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%"""
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """REPO=/home/%env.USER%/.m2/repository
if [ -e ${'$'}REPO ] ; then
rm -rf ${'$'}REPO
echo "${'$'}REPO was polluted during the build"
return 1
else
echo "${'$'}REPO does not exist"
fi"""
        }
    }

    failureConditions {
        executionTimeoutMin = 420
    }

    dependencies {
        dependency(Gradle_Branches_CommitPhase.buildTypes.Gradle_Branches_CommitPhase_BuildDistributions) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }

            artifacts {
                cleanDestination = true
                artifactRules = """distributions/*-all.zip => incoming-distributions
        build-receipt.properties => incoming-distributions"""
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
