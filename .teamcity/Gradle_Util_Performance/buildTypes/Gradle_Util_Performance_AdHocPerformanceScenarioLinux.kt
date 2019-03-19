package Gradle_Util_Performance.buildTypes

import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildStep
import jetbrains.buildServer.configs.kotlin.v2018_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2018_2.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v2018_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

object Gradle_Util_Performance_AdHocPerformanceScenarioLinux : BuildType({
    uuid = "a3183d81-e07d-475c-8ef6-04ed60bf4053"
    name = "AdHoc Performance Scenario - Linux"

    artifactRules = """
        subprojects/*/build/test-results-*.zip => results
        subprojects/*/build/tmp/**/log.txt => failure-logs
    """.trimIndent()

    params {
        text("baselines", "defaults", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("templates", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("channel", "adhoc")
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.PATH", "%env.PATH%:/opt/swift/latest/usr/bin")
        param("env.JAVA_HOME", "%linux.java11.openjdk.64bit%")
        param("flamegraphs", "--flamegraphs true")
        param("checks", "all")
        param("env.FG_HOME_DIR", "/opt/FlameGraph")
        param("additional.gradle.parameters", "")
        param("env.HP_HOME_DIR", "/opt/honest-profiler")
        text("scenario", "", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("warmups", "3", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("performance.db.username", "tcagent")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        text("runs", "10", display = ParameterDisplay.PROMPT, allowEmpty = false)
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
    }

    vcs {
        root(AbsoluteId("Gradle_Branches_GradlePersonalBranches"))

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            gradleParams = """clean %templates% performance:performanceAdHocTest --scenarios "%scenario%" --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% %flamegraphs% -x prepareSamples  -PmaxParallelForks=%maxParallelForks% %additional.gradle.parameters% -Dorg.gradle.logging.level=LIFECYCLE -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts --build-cache "-Dgradle.cache.remote.url=%gradle.cache.remote.url%" "-Dgradle.cache.remote.username=%gradle.cache.remote.username%" "-Dgradle.cache.remote.password=%gradle.cache.remote.password%" -PtestJavaHome=%linux.java8.oracle.64bit% -Porg.gradle.performance.branchName=%teamcity.build.branch%"""
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                REPO=/home/%env.USER%/.m2/repository
                if [ -e ${'$'}REPO ] ; then
                rm -rf ${'$'}REPO
                echo "${'$'}REPO was polluted during the build"
                exit 1
                else
                echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
    }

    failureConditions {
        executionTimeoutMin = 420
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
