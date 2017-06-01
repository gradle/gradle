package Gradle_Check_TestCoverageForkedJava9Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageForkedJava9Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "8b8d6838-c872-44eb-8c1c-474994348e80"
    extId = "Gradle_Check_TestCoverageForkedJava9Linux_1"
    name = "Test Coverage - Forked Java9 Linux (1)"

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
