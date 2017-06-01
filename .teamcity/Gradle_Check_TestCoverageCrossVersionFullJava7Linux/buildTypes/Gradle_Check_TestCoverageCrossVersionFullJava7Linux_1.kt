package Gradle_Check_TestCoverageCrossVersionFullJava7Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageCrossVersionFullJava7Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "0b632e16-4bfe-439b-a4fb-b248d4f36bdb"
    extId = "Gradle_Check_TestCoverageCrossVersionFullJava7Linux_1"
    name = "Test Coverage - Cross-version Full Java7 Linux (1)"

    params {
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "crossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
