package Gradle_Check_TestCoverageNoDaemonJava8Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.*

object Gradle_Check_TestCoverageNoDaemonJava8Linux_1 : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "8c23fc7a-fc66-4317-8f60-f9562c174946"
    extId = "Gradle_Check_TestCoverageNoDaemonJava8Linux_1"
    name = "No-daemon Java8 Linux (1)"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("org.gradle.test.bucket", "1")
        param("org.gradle.test.buildType", "noDaemon")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
