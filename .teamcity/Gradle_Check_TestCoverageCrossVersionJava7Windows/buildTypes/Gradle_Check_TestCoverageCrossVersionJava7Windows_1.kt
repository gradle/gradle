package Gradle_Check_TestCoverageCrossVersionJava7Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageCrossVersionJava7Windows_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "e96578b9-fa11-47c8-99b0-2e24f2cbe2e1"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Windows_1"
    name = "Test Coverage - Cross-version Java7 Windows (1)"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "quickFeedbackCrossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
