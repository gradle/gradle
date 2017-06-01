package Gradle_Check_Stage2_TestCoverageEmbeddedJava8Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageEmbeddedJava8Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageEmbeddedLinux)
    uuid = "a7ad9017-cb90-4703-b1e5-9dd6508626c6"
    extId = "Gradle_Check_TestCoverageEmbeddedJava8Linux_1"
    name = "Test Coverage - Embedded Java8 Linux (1)"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("gradle.test.bucket", "1")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
