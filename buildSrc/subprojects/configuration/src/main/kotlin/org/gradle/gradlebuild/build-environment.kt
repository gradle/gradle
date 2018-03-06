package org.gradle.gradlebuild

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem


object BuildEnvironment {
    val isCiServer = "CI" in System.getenv()
    val gradleKotlinDslVersion = "1.0-SNAPSHOT"
    val jvm = org.gradle.internal.jvm.Jvm.current()
    val javaVersion = JavaVersion.current()
    val isWindows = OperatingSystem.current().isWindows
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
