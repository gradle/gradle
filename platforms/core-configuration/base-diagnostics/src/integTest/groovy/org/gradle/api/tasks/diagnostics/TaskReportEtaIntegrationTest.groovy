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

package org.gradle.api.tasks.diagnostics

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture

class TaskReportEtaIntegrationTest extends AbstractIntegrationSpec {

    def historyFile = file(".gradle/task-durations.txt")

    def setup() {
        settingsFile << "rootProject.name = 'test'\n"
        buildFile << """
            tasks.register("known") { group = "Custom" }
            tasks.register("fresh") { group = "Custom" }
        """
    }

    def "shows recorded durations and unknown"() {
        given:
        seed(":known\t1000,2000,3000")

        when:
        succeeds "tasks", "--eta"

        then:
        outputContains("""
Custom tasks
------------
fresh (ETA: unknown)
known (ETA: avg 2.000s; last 1.000s, 2.000s, 3.000s)
""")
    }

    def "does not show durations without --eta"() {
        given:
        seed(":known\t1000")

        when:
        succeeds "tasks"

        then:
        outputContains("""
Custom tasks
------------
fresh
known
""")
        outputDoesNotContain("ETA")
    }

    def "shows multiple tasks for tasks merged across projects"() {
        given:
        createDirs("a")
        settingsFile << "include 'a'\n"
        file("a/build.gradle") << """
            tasks.register("known") { group = "Custom" }
        """
        seed(":known\t1000", ":a:known\t2000")

        when:
        succeeds "tasks", "--eta"

        then:
        outputContains("known (ETA: multiple tasks)")
        outputContains("fresh (ETA: unknown)")

        when:
        succeeds "tasks", "--eta", "--all"

        then:
        outputContains("a:known (ETA: avg 2.000s; last 2.000s)")
        outputContains("\nknown (ETA: avg 1.000s; last 1.000s)")
    }

    def "custom task report works with and without --eta"() {
        given:
        buildFile << """
            tasks.register("myTasks", TaskReportTask)
        """
        seed(":known\t1000")

        when:
        succeeds "myTasks"

        then:
        outputContains("\nknown\n")

        when:
        succeeds "myTasks", "--eta"

        then:
        outputContains("known (ETA: avg 1.000s; last 1.000s)")
    }

    def "shows multiple tasks when a same-named task is in another group or hidden"() {
        given:
        createDirs("a", "b")
        settingsFile << "include 'a', 'b'\n"
        file("a/build.gradle") << """
            tasks.register("known") { group = "Other" }
        """
        file("b/build.gradle") << """
            tasks.register("known")
        """
        seed(":known\t1000", ":a:known\t2000", ":b:known\t3000")

        when:
        succeeds "tasks", "--eta"

        then:
        outputContains("""
Custom tasks
------------
fresh (ETA: unknown)
known (ETA: multiple tasks)
""")
        outputContains("""
Other tasks
-----------
known (ETA: multiple tasks)
""")
    }

    def "reads current durations on configuration cache hit"() {
        def configurationCache = new ConfigurationCacheFixture(this)

        given:
        seed(":known\t1000")

        when:
        succeeds "tasks", "--eta", "--${StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION}"

        then:
        configurationCache.assertStateStored()
        outputContains("known (ETA: avg 1.000s; last 1.000s)")

        when:
        seed(":known\t5000")
        succeeds "tasks", "--eta", "--${StartParameterBuildOptions.ConfigurationCacheOption.LONG_OPTION}"

        then:
        configurationCache.assertStateLoaded()
        outputContains("known (ETA: avg 5.000s; last 5.000s)")
    }

    private void seed(String... entries) {
        historyFile.text = (["# Gradle task duration history", "# format: 1"] + (entries as List)).join("\n") + "\n"
    }
}
