package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_PerformanceTestCoordinatorLinux : BuildType({
    uuid = "f9e65521-c753-44e3-97f5-9933508f8242"
    extId = "Gradle_Branches_Performance_PerformanceTestCoordinatorLinux"
    name = "Performance Test Coordinator - Linux"

    artifactRules = """build/reports/** => reports
subprojects/*/build/performance-tests/** => results
"""
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
            gradleParams = "-I ./gradle/buildScanInit.gradle cleanDistributedPerformanceTest distributedPerformanceTests -x prepareSamples -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue --baselines 'defaults,nightly' -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=Gradle_Branches_Performance_IndividualPerformanceScenarioWorkersLinux -Porg.gradle.performance.branchName=%teamcity.build.branch% -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent%"
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
        Gradle_Branches_CommitPhase_LinuxCommit_LinuxCommitJava18.Project.buildTypes.forEach { buildType ->
            dependency(buildType) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
