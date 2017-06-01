package Gradle_Check_TestCoverageEmbeddedJava8Linux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageEmbeddedJava8Linux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageEmbeddedLinux)
    uuid = "a7ad9017-cb90-4703-b1e5-9dd6508626c6"
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
