package Gradle_Check_Stage4.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.GradleBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.gradle
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Check_GradleceptionJava8Linux : BuildType({
    uuid = "6b4a6e81-324a-4673-a812-1f4db738e562"
    extId = "Gradle_Check_GradleceptionJava8Linux"
    name = "Gradleception - Java8 Linux"
    description = "Builds Gradle with the version of Gradle which is currently under development (twice)"

    artifactRules = """
        **/build/reports/** => reports
        subprojects/*/build/tmp/test files/** => test-files
        subprojects/*/build/tmp/test distros/**/user-home-dir/daemon/*.log => isolated-daemon
        build/daemon/** => daemon
        intTestHomeDir/worker-1/daemon/**/*.log => intTestHome-daemon
        build/errorLogs/** => errorLogs
    """.trimIndent()
    maxRunningBuilds = 3

    params {
        param("env.GRADLE_OPTS", "-Xmx1G -Dfile.encoding=utf-8")
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        gradle {
            name = "BUILD_WITH_WRAPPER"
            tasks = "clean :install -Pgradle_installPath=dogfood-first"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
        gradle {
            name = "BUILD_WITH_BUILT_GRADLE"
            tasks = "clean :install -Pgradle_installPath=dogfood-second"
            gradleHome = "%teamcity.build.checkoutDir%/dogfood-first"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle -Djava7.home=%linux.jdk.for.gradle.compile%"
            param("ui.gradleRunner.gradle.wrapper.useWrapper", "false")
        }
        gradle {
            name = "QUICKCHECK_WITH_GRADLE_BUILT_BY_GRADLE"
            tasks = "clean quickCheck"
            gradleHome = "%teamcity.build.checkoutDir%/dogfood-second"
            gradleParams = "-PmaxParallelForks=%maxParallelForks% -s --no-daemon --continue -I ./gradle/buildScanInit.gradle -Djava7.home=%linux.jdk.for.gradle.compile%"
            param("ui.gradleRunner.gradle.wrapper.useWrapper", "false")
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
        gradle {
            name = "TAG_BUILD"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            tasks = "tagBuild"
            buildFile = "gradle/buildTagging.gradle"
            gradleParams = "-PteamCityUsername=%teamcity.username.restbot% -PteamCityPassword=%teamcity.password.restbot% -PteamCityBuildId=%teamcity.build.id% -PgithubToken=%github.ci.oauth.token% -Djava7.home=%linux.jdk.for.gradle.compile%"
            useGradleWrapper = true
        }
    }

    failureConditions {
        executionTimeoutMin = 240
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
