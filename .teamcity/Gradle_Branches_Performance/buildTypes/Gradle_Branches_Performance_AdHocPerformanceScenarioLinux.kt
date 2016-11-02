package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_AdHocPerformanceScenarioLinux : BuildType({
    uuid = "79b66aae-1df3-4912-ae15-96a2e3bfa278"
    extId = "Gradle_Branches_Performance_AdHocPerformanceScenarioLinux"
    name = "AdHoc Performance Scenario - Linux"

    artifactRules = """build/reports/** => reports
                            subprojects/*/build/test-results-*.zip => results"""

    params {
        param("additional.gradle.parameters", "")
        param("baselines", "defaults")
        param("channel", "adhoc")
        param("checks", "all")
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.FG_HOME_DIR", "/opt/FlameGraph")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.HP_HOME_DIR", "/opt/honest-profiler")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("runs", "10")
        param("scenario", "")
        param("templates", "")
        param("warmups", "3")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            gradleParams = """cleanSamples cleanPerformanceAdHocTest %templates% performanceAdHocTest --scenarios "%scenario%" --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% --flamegraphs true -x prepareSamples -x performanceReport -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% %additional.gradle.parameters% -s --no-daemon --continue"""
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

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
