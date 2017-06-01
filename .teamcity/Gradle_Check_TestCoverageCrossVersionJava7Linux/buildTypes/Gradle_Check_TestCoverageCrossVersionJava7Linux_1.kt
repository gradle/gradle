package Gradle_Check_TestCoverageCrossVersionJava7Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageCrossVersionJava7Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "78508f82-0840-41ee-80d8-8d86eb553e36"
    extId = "Gradle_Check_TestCoverageCrossVersionJava7Linux_$bucket"
    name = "Test Coverage - Cross-version Java7 Linux ($bucket)"

    params {
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "quickFeedbackCrossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
