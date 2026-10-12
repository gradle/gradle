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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.integtests.fixtures.daemon.DaemonsFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/20089")
class ConfigurationCacheJdkModuleAccessIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "restores task field referencing JDK type from non-open module"() {
        buildFile << colorTask()

        expect:
        configurationCacheRun "ok"
        outputContains("color = java.awt.Color[r=1,g=2,b=3]")
    }

    @Requires(
        value = TestExecutionPreconditions.NotEmbeddedExecutor,
        reason = "needs a real daemon process so the load path runs in a fresh JVM with no warmed reflection caches"
    )
    def "loads task field referencing JDK type from non-open module after daemon restart"() {
        executer.requireDaemon().requireIsolatedDaemons()
        buildFile << colorTask()

        when:
        configurationCacheRun "ok"

        then:
        outputContains("color = java.awt.Color[r=1,g=2,b=3]")

        when: 'daemon is killed between store and load to drop bean-schema and codec caches'
        daemons.killAll()

        and:
        configurationCacheRun "ok"

        then:
        outputContains("color = java.awt.Color[r=1,g=2,b=3]")
        outputContains("Reusing configuration cache.")
    }

    private String colorTask() {
        buildScriptSnippet """
            class ColorTask extends DefaultTask {
                private java.awt.Color color = new java.awt.Color(1, 2, 3)
                @TaskAction
                void run() { println "color = " + color }
            }
            tasks.register("ok", ColorTask)
        """
    }

    private DaemonsFixture getDaemons() {
        new DaemonLogsAnalyzer(executer.daemonBaseDir)
    }
}
