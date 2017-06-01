package Gradle_Check_TestCoverageCrossVersionFullJava7Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageCrossVersionFullJava7Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "Gradle_Check_TestCoverageCrossVersionFullJava7Linux_$bucket"
    extId = "Gradle_Check_TestCoverageCrossVersionFullJava7Linux_$bucket"
    name = "Test Coverage - Cross-version Full Java7 Linux ($bucket)"

    params {
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "crossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
