package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_PerformanceHistoricalBuildLinux : BuildType({
    uuid = "5f4bc55f-8c1f-4267-a024-8bd5eee1f28c"
    extId = "Gradle_Branches_Performance_PerformanceHistoricalBuildLinux"
    name = "Performance Historical Build - Linux"
    description = "A complete performance test suite that executes against a lot more Gradle versions, weekly."

    artifactRules = """build/reports/** => reports
subprojects/*/build/performance-tests/** => results"""
    maxRunningBuilds = 1

    params {
        param("baselines", "1.1,1.12,2.0,2.1,2.4,2.9,2.12,2.14.1,last")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("TC_USERNAME", "TeamcityRestBot")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            gradleParams = "-I ./gradle/buildScanInit.gradle cleanDistributedFullPerformanceTest distributedFullPerformanceTests -x prepareSamples -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% --baselines %baselines% --checks none -s --no-daemon --continue -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%"
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
        executionTimeoutMin = 2280
        testFailure = false
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
