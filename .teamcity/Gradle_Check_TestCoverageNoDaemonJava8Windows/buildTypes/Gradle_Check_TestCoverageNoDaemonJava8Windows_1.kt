package Gradle_Check_TestCoverageNoDaemonJava8Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageNoDaemonJava8Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "bda20233-f0f8-47be-8076-16c4ac0e7e2c"
    extId = "Gradle_Check_TestCoverageNoDaemonJava8Windows_$bucket"
    name = "Test Coverage - No-daemon Java8 Windows ($bucket)"

    params {
        param("env.JAVA_HOME", "%windows.java8.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "noDaemon")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
