package org.gradle.gradlebuild

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem


object BuildEnvironment {
    val isCiServer = "CI" in System.getenv()
    val isTravis = "TRAVIS" in System.getenv()
    val isJenkins = "JENKINS_HOME" in System.getenv()
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
