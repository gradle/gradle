package Gradle_Check_TestCoverageForkedJava8Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_Stage4_TestCoverageForkedJava8Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "5311ca7c-522d-4871-90d4-186206e42788"
    extId = "Gradle_Check_Stage4_TestCoverageForkedJava8Windows_$bucket"
    name = "Test Coverage - Forked Java8 Windows ($bucket)"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "platform")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
