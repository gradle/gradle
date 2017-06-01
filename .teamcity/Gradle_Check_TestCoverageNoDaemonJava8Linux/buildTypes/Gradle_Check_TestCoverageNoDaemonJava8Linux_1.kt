package Gradle_Check_TestCoverageNoDaemonJava8Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageNoDaemonJava8Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "Gradle_Check_TestCoverageNoDaemonJava8Linux_$bucket"
    extId = "Gradle_Check_TestCoverageNoDaemonJava8Linux_$bucket"
    name = "No-daemon Java8 Linux ($bucket)"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "noDaemon")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
