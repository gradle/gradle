package Gradle_Check_TestCoverageCrossVersionJava7Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageCrossVersionJava7Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "e96578b9-fa11-47c8-99b0-2e24f2cbe2e1"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Windows_$bucket"
    name = "Test Coverage - Cross-version Java7 Windows ($bucket)"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "quickFeedbackCrossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
