package Gradle_Check_TestCoverageCrossVersionJava7Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageCrossVersionJava7Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "78508f82-0840-41ee-80d8-8d86eb553e36"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Linux_1"
    name = "Test Coverage - Cross-version Java7 Linux (1)"

    params {
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "quickFeedbackCrossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
