package Gradle_Check_TestCoverageParallelJava7IBMLinux.buildTypes

import jetbrains.buildServer.configs.kotlin.v10.BuildType

class Gradle_Check_TestCoverageParallelJava7IBMLinux_1(bucket: String) : BuildType({
    template(Gradle_Check.buildTypes.Gradle_Check_TestCoverageForkedLinux)
    uuid = "3adf743c-f725-4956-bae5-2f81fc22411d"
    extId = "Gradle_Check_TestCoverageParallelJava7IBMLinux_$bucket"
    name = "Test Coverage - Parallel Java7IBM Linux ($bucket)"

    params {
        param("env.JAVA_HOME", "%linux.java7.ibm.64bit%")
        param("org.gradle.test.bucket", bucket)
        param("org.gradle.test.buildType", "parallel")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
    }
})
