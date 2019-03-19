package Gradle_Util_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v2018_2.buildSteps.script

object Gradle_Util_Performance_PerformanceTestCoordinatorLinux : BuildType({
    uuid = "a28ced77-77d1-41fd-bc3b-fe9c9016bf7b"
    name = "AdHoc Performance Test Coordinator - Linux"

    artifactRules = """
        build/report-* => .
        buildSrc/build/report-* => .
        subprojects/*/build/tmp/test files/** => test-files
        build/errorLogs/** => errorLogs
    """.trimIndent()
    detectHangingBuilds = false
    maxRunningBuilds = 2

    params {
        param("performance.baselines", "defaults")
        param("performance.db.username", "tcagent")
        param("TC_USERNAME", "TeamcityRestBot")
        param("env.JAVA_HOME", "%linux.java11.openjdk.64bit%")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
    }

    vcs {
        root(AbsoluteId("Gradle_Branches_GradlePersonalBranches"))

        checkoutMode = CheckoutMode.ON_AGENT
        buildDefaultBranch = false
    }

    steps {
        gradle {
            name = "GRADLE_RUNNER"
            tasks = ""
            buildFile = ""
            gradleParams = "clean distributedPerformanceTests -x prepareSamples --baselines %performance.baselines%  -PtimestampedVersion -Porg.gradle.performance.branchName=%teamcity.build.branch% -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -PteamCityUsername=%TC_USERNAME% -PteamCityPassword=%teamcity.password.restbot% -Porg.gradle.performance.buildTypeId=Gradle_Check_IndividualPerformanceScenarioWorkersLinux -Porg.gradle.performance.workerTestTaskName=fullPerformanceTest -Porg.gradle.performance.coordinatorBuildId=%teamcity.build.id% -Porg.gradle.performance.db.password=%performance.db.password.tcagent% -PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/init-scripts/build-scan.init.gradle.kts --build-cache -Dgradle.cache.remote.url=%gradle.cache.remote.url% -Dgradle.cache.remote.username=%gradle.cache.remote.username% -Dgradle.cache.remote.password=%gradle.cache.remote.password% -PtestJavaHome=%linux.java8.oracle.64bit%"
            param("ui.gradleRunner.gradle.build.file", "")
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
                    exit 1
                else
                    echo "${'$'}REPO does not exist"
                fi
            """.trimIndent()
        }
        gradle {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = ""
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token%"
            param("ui.gradleRunner.gradle.build.file", "")
        }
    }

    failureConditions {
        executionTimeoutMin = 420
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "Gradle_Branches_GradlePersonalBranches"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = personalToken {
                    token = "credentialsJSON:5306bfc7-041e-46e8-8d61-1d49424e7b04"
                }
            }
        }
    }

    dependencies {
        dependency(AbsoluteId("Gradle_Check_CompileAll")) {
            snapshot {
                onDependencyFailure = FailureAction.CANCEL
                onDependencyCancel = FailureAction.CANCEL
            }

            artifacts {
                cleanDestination = true
                artifactRules = "build-receipt.properties => incoming-distributions"
            }
        }
    }

    requirements {
        contains("teamcity.agent.jvm.os.name", "Linux")
    }
})
