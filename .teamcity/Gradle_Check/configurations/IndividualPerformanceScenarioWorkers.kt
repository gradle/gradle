package configurations

import common.Os
import common.applyDefaultSettings
import common.buildToolGradleParameters
import common.checkCleanM2
import common.gradleWrapper
import jetbrains.buildServer.configs.kotlin.v2018_2.AbsoluteId
import model.CIBuildModel

class IndividualPerformanceScenarioWorkers(model: CIBuildModel) : BaseGradleBuildType(model, init = {
    uuid = model.projectPrefix + "IndividualPerformanceScenarioWorkersLinux"
    id = AbsoluteId(uuid)
    name = "Individual Performance Scenario Workers - Linux"

    applyDefaultSettings(timeout = 420)
    artifactRules = """
        subprojects/*/build/test-results-*.zip => results
        subprojects/*/build/tmp/**/log.txt => failure-logs
    """.trimIndent()

    params {
        param("baselines", "defaults")
        param("channel", "commits")
        param("checks", "all")
        param("runs", "defaults")
        param("warmups", "defaults")
        param("templates", "")
        param("scenario", "")

        param("performance.db.url", "jdbc:h2:ssl://dev61.gradle.org:9092")
        param("performance.db.username", "tcagent")

        param("env.GRADLE_OPTS", "-Xmx1536m -XX:MaxPermSize=384m")
        param("env.ANDROID_HOME", "/opt/android/sdk")
        param("env.JAVA_HOME", buildJavaHome)
        param("env.BUILD_BRANCH", "%teamcity.build.branch%")
        param("env.PATH", "%env.PATH%:/opt/swift/latest/usr/bin")
    }

    steps {
        gradleWrapper {
            name = "GRADLE_RUNNER"
            tasks = ""
            gradleParams = (
                    buildToolGradleParameters(isContinue = false)
                    + listOf("""clean %templates% fullPerformanceTests --scenarios "%scenario%" --baselines %baselines% --warmups %warmups% --runs %runs% --checks %checks% --channel %channel% -x prepareSamples -Porg.gradle.performance.branchName=%teamcity.build.branch% -Porg.gradle.performance.db.url=%performance.db.url% -Porg.gradle.performance.db.username=%performance.db.username% -Porg.gradle.performance.db.password=%performance.db.password.tcagent% -PtimestampedVersion""",
                            buildScanTag("IndividualPerformanceScenarioWorkers"), "-PtestJavaHome=${individualPerformanceTestJavaHome}")
                            + model.parentBuildCache.gradleParameters(Os.linux)
                    ).joinToString(separator = " ")
        }
        checkCleanM2()
    }

    applyDefaultDependencies(model, this)
})
