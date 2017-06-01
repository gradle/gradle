package Gradle_Check_TestCoverageForkedJava7Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageForkedJava7Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "10e384cf-e5fc-4b6c-abe9-cf0b52766a5f"
    extId = "Gradle_Check_TestCoverageForkedJava7Linux_1"
    name = "Test Coverage - Forked Java7 Linux (1)"

    params {
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "platform")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
