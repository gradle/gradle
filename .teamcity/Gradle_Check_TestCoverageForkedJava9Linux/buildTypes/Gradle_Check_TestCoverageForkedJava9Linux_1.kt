package Gradle_Check_TestCoverageForkedJava9Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageForkedJava9Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "Gradle_Check_TestCoverageForkedJava9Linux_$bucket"
    extId = "Gradle_Check_TestCoverageForkedJava9Linux_$bucket"
    name = "Test Coverage - Forked Java9 Linux ($bucket)"

    params {
        param("env.JAVA_HOME", "%linux.java9.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "platform")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
