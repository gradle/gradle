package Gradle_Check_TestCoverageForkedJava8Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_Stage4_TestCoverageForkedJava8Windows_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "5311ca7c-522d-4871-90d4-186206e42788"
    extId = "Gradle_Check_Stage4_TestCoverageForkedJava8Windows_1"
    name = "Test Coverage - Forked Java8 Windows (1)"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "platform")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
