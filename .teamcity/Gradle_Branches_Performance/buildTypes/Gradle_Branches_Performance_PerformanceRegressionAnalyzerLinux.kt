package Gradle_Branches_Performance.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.ScriptBuildStep.*
import jetbrains.buildServer.configs.kotlin.v10.buildSteps.script

object Gradle_Branches_Performance_PerformanceRegressionAnalyzerLinux : BuildType({
    uuid = "1922053d-e362-4575-99e7-7e3b604ebaac"
    extId = "Gradle_Branches_Performance_PerformanceRegressionAnalyzerLinux"
    name = "Performance Regression Analyzer - Linux"

    artifactRules = "build/reports/** => reports"
    maxRunningBuilds = 1

    params {
        param("baselines", "defaults")
        param("bisect.bad", "")
        param("bisect.good", "")
        param("checks", "all")
        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.JAVA_HOME", "/opt/jdk/oracle-jdk-8-latest")
        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")
        param("runs", "defaults")
        param("scenario", "")
        param("TC_USERNAME", "TeamcityRestBot")
        param("templates", "")
        param("warmups", "defaults")
    }

    vcs {
        root("Gradle_Branches_GradlePersonalBranches")

        checkoutMode = CheckoutMode.ON_AGENT
    }

    steps {
        script {
            name = "Bisect regression"
            scriptContent = """git bisect start
git bisect good %bisect.good%
git bisect bad %bisect.bad%
git bisect run ./gradlew cleanSamples cleanPerformanceAdHocTest %templates% performanceAdhocTest --scenarios "%scenario%"  --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% -x prepareSamples -x performanceReport --no-daemon --continue"""
        }
        script {
            name = "Reset"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            scriptContent = "git bisect reset"
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
