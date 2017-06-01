package Gradle_Check_TestCoverageNoDaemonJava8Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageNoDaemonJava8Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "Gradle_Check_TestCoverageNoDaemonJava8Windows_$bucket"
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
