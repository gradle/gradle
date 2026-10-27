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

package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.junit.Test
import spock.lang.Issue


class KotlinDslConfigurationCacheIdeaIoIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    @Issue("https://github.com/gradle/gradle/issues/30145")
    @Requires(TestExecutionPreconditions.NotConfigCached::class, reason = "drives configuration cache explicitly")
    fun `configuration cache is reused across daemons when build logic reads idea_io_use_nio2`() {

        withBuildScript(
            """
            // Read it at configuration time (as some plugins do) to make it a cache input
            println("idea.io.use.nio2 = " + System.getProperty("idea.io.use.nio2"))

            tasks.register("noop")
            """
        )

        executer.requireDaemon().requireIsolatedDaemons()
        build("--configuration-cache", "noop")
            .assertOutputContains("Calculating task graph as no cached configuration is available")

        // Kill the daemon to clear system properties
        DaemonLogsAnalyzer(executer.daemonBaseDir).killAll()
        build("--configuration-cache", "noop")
            .assertOutputContains("Reusing configuration cache.")
    }
}
