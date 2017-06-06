package Gradle_Check_Stage7.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildStep
import jetbrains.buildServer.configs.kotlin.v10.BuildType
import jetbrains.buildServer.configs.kotlin.v10.CheckoutMode
import jetbrains.buildServer.configs.kotlin.v10.FailureAction
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_PerformanceHistoricalBuild : BuildType({
    uuid = "9103834b-be4d-43b9-87ac-7fcae33bfc93"
    extId = "Gradle_Check_PerformanceHistoricalBuild"
    name = "Performance Historical Build - Linux"
    description = "A complete performance test suite that executes against a lot more Gradle versions, weekly."

    artifactRules = "subprojects/*/build/performance-tests/** => results"
    maxRunningBuilds = 1

    params {
        param("baselines", "2.9,2.12,2.14.1,last")
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
            gradleParams = "cleanDistributedFullPerformanceTest distributedFullPerformanceTests -x prepareSamples -PtimestampedVersion -PmaxParallelForks=%maxParallelForks% --baselines %baselines% --checks none -s --no-daemon --continue -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=Gradle_Util_IndividualPerformanceScenarioWorkersLinux -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent% -Porg.gradle.performance.branchName=%teamcity.build.branch% -Djava7.home=%linux.jdk.for.gradle.compile% -I ./gradle/buildScanInit.gradle --build-cache -Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password% -I ./gradle/taskCacheDetailedStatsInit.gradle"
            useGradleWrapper = true
        }
        script {
            name = "CHECK_CLEAN_M2"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = """
                REPO=/home/%env.USER%/.m2/repository
                if [ -e ${'$'}REPO ] ; then
                tree ${'$'}REPO
                rm -rf ${'$'}REPO
                echo "${'$'}REPO was polluted during the build"
                return 1
                else
                echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
    }

    failureConditions {
        executionTimeoutMin = 2280
        javaCrash = false
    }

    dependencies {
        dependency(Gradle_Check_Stage3.buildTypes.Gradle_Check_Stage3_Passes) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }
        }
        artifacts(Gradle_Check_Stage1.buildTypes.Gradle_Check_Stage1_BuildDistributions) {
            cleanDestination = true
            artifactRules = """
                distributions/*-all.zip => incoming-distributions
                build-receipt.properties => incoming-distributions
            """.trimIndent()
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
