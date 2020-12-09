/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gradlebuild.basics

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem


fun Project.testDistributionEnabled() = providers.systemProperty("enableTestDistribution").forUseAtConfigurationTime().orNull?.toBoolean() == true


object BuildEnvironment {

    const val CI_ENVIRONMENT_VARIABLE = "CI"
    const val BUILD_BRANCH = "BUILD_BRANCH"
    const val BUILD_COMMIT_ID = "BUILD_COMMIT_ID"
    const val BUILD_VCS_NUMBER = "BUILD_VCS_NUMBER"

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
