package Gradle_Check_TestCoverageEmbeddedJava7Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageEmbeddedJava7Windows_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageEmbeddedWindows)
    uuid = "809a2f3a-644e-41fd-be19-3b3fcad572cf"
    extId = "Gradle_Check_TestCoverageEmbeddedJava7Windows_1"
    name = "Test Coverage - Embedded Java7 Windows (1)"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", "1")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
