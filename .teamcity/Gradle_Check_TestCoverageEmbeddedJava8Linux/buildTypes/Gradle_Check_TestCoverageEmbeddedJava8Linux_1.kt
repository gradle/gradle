package Gradle_Check_TestCoverageEmbeddedJava8Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageEmbeddedJava8Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageEmbeddedLinux)
    uuid = "Gradle_Check_TestCoverageEmbeddedJava8Linux_$bucket"
    extId = "Gradle_Check_TestCoverageEmbeddedJava8Linux_$bucket"
    name = "Test Coverage - Embedded Java8 Linux ($bucket)"

    params {
        param("env.JAVA_HOME", "%linux.java8.oracle.64bit%")
        param("gradle.test.bucket", bucket)
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
