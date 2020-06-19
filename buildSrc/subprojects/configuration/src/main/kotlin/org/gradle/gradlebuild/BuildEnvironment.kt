package org.gradle.gradlebuild

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem


object BuildEnvironment {

    const val CI_ENVIRONMENT_VARIABLE = "CI"
    const val BUILD_BRANCH = "BUILD_BRANCH"
    const val BUILD_COMMIT_ID = "BUILD_COMMIT_ID"

    val isCiServer = CI_ENVIRONMENT_VARIABLE in System.getenv()
    val isIntelliJIDEA by lazy { System.getProperty("idea.version") != null }
    val isTravis = "TRAVIS" in System.getenv()
    val isJenkins = "JENKINS_HOME" in System.getenv()
    val isGhActions = "GITHUB_ACTIONS" in System.getenv()
    val jvm = org.gradle.internal.jvm.Jvm.current()
    val javaVersion = JavaVersion.current()
    val isWindows = OperatingSystem.current().isWindows
    val isSlowInternetConnection
        get() = System.getProperty("slow.internet.connection", "false")!!.toBoolean()
    val agentNum: Int
        get() {
            if (System.getenv().containsKey("USERNAME")) {
                val agentNumEnv = System.getenv("USERNAME").replaceFirst("tcagent", "")
                if (Regex("""\d+""").containsMatchIn(agentNumEnv)) {
                    return agentNumEnv.toInt()
                }
            }
            return 1
        }
}
