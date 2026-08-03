/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.daemon

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec

class ForegroundDaemonIntegrationTest extends DaemonIntegrationSpec {

    def "build emits a warning instead of failing when the foreground daemon cannot mutate its environment to match the client"() {
        // JVM args for the foreground daemon. We explicitly run the foreground daemon
        // without the necessary --add-opens flags, so that the foreground daemon will
        // not be able to mutate its environment to match the client.
        List<String> jvmArgs = ["-ea", "-Xms256m", "-Xmx512m"]

        given:
        executer.useOnlyRequestedJvmOpts()
        executer.withBuildJvmOpts(jvmArgs)
        def foregroundDaemon = startAForegroundDaemon()

        buildFile """
            tasks.register("echoEnv") {
                doLast {
                    println "CUSTOM_VAR=" + (System.getenv("CUSTOM_VAR") ?: "<not set>")
                }
            }
        """

        when:
        executer.useOnlyRequestedJvmOpts()
        // We must execute with the same JVM args as the foreground daemon, so we run our
        // build in that daemon instead of starting a new one.
        executer.withArgument("-Dorg.gradle.jvmargs=${jvmArgs.join(" ")}")
        executer.withEnvironmentVars(CUSTOM_VAR: "custom-value")
        succeeds("echoEnv")

        then:
        // Make sure we didn't start a new daemon.
        daemons.registry.all.size() == 1

        and:
        outputContains("Unable to set daemon's environment variables to match the client")
        outputContains("CUSTOM_VAR=<not set>")

        cleanup:
        foregroundDaemon?.abort()
    }

}
