package Gradle_Check_TestCoverageCrossVersionFullJava7Windows.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageCrossVersionFullJava7Windows_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedWindows)
    uuid = "Gradle_Check_TestCoverageCrossVersionFullJava7Windows_$bucket"
    extId = "Gradle_Check_TestCoverageCrossVersionFullJava7Windows_$bucket"
    name = "Test Coverage - Cross-version Full Java7 Windows ($bucket)"

    params {
        param("env.JAVA_HOME", "%windows.java7.oracle.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "crossVersion")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
