package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_PerformanceExperimentsCoordinatorLinux : BuildType({
    uuid = "3504f187-ecba-4cdd-bd54-5f56c258f1f3"
    extId = "Gradle_Branches_Performance_PerformanceExperimentsCoordinatorLinux"
    name = "Performance Experiments Coordinator - Linux"

    artifactRules = """build/reports/** => reports
subprojects/*/build/performance-tests/** => results"""
    detectHangingBuilds = false
    maxRunningBuilds = 1

    params {
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
            gradleParams = "-I ./gradle/buildScanInit.gradle cleanDistributedPerformanceExperiment distributedPerformanceExperiments -x prepareSamples -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%"
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
