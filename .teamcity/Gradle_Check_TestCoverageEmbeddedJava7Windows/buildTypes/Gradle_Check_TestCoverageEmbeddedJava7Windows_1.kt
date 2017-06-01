package Gradle_Check_TestCoverageEmbeddedJava7Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageEmbeddedJava7Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageEmbeddedWindows)
    uuid = "809a2f3a-644e-41fd-be19-3b3fcad572cf"
    extId = "Gradle_Check_TestCoverageEmbeddedJava7Windows_$bucket"
    name = "Test Coverage - Embedded Java7 Windows ($bucket)"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
