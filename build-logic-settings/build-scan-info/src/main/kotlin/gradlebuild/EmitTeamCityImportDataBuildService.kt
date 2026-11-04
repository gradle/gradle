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

package gradlebuild

import org.gradle.api.provider.MapProperty
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test

/**
 * This is a workaround for https://youtrack.jetbrains.com/issue/TW-76894.
 *
 * In short, we want TeamCity to be aware of the test execution data (which tests are executed and how long they are),
 * even when the Test task is `FROM-CACHE` or `UP-TO-DATE`. This build service outputs a service message to instruct
 * TeamCity to read the JUnit test result XMLs of such a task.
 *
 * See https://www.jetbrains.com/help/teamcity/service-messages.html#Importing+XML+Reports
 */
abstract class EmitTeamCityImportDataBuildService :
    AbstractBuildScanInfoCollectingService<EmitTeamCityImportDataBuildService.Params>() {

    interface Params : BuildServiceParameters {
        /**
         * Key is the path of a project of the main build, value is its project directory relative to the repo root,
         * with `/` as the separator. The root project maps to an empty string.
         *
         * The JUnit XML directory of a test task cannot be read here, because looking up the tasks of another project
         * is what Isolated Projects forbids. It is derived from the project directory instead, which settings already
         * knows, and the default `test-results/<task name>` layout, which this build does not override anywhere.
         */
        val projectPathToRelativeProjectDir: MapProperty<String, String>
    }

    override fun isMonitoredTask(taskClass: Class<*>) = Test::class.java.isAssignableFrom(taskClass)

    override fun action(taskPath: String, outcome: TaskOutcome) {
        if (outcome != TaskOutcome.UP_TO_DATE && outcome != TaskOutcome.FROM_CACHE) {
            // A task that ran its actions reported its tests to TeamCity while running, and a task that was
            // SKIPPED or NO-SOURCE has no results to import.
            return
        }
        val outputXmlPath = junitXmlLocationOf(taskPath) ?: return
        println("##teamcity[importData type='junit' path='$outputXmlPath/TEST-*.xml' verbose='true']")
    }

    /**
     * Returns `null` for a task of an included build, whose projects are not in the map.
     */
    private
    fun junitXmlLocationOf(taskPath: String): String? {
        val lastColon = taskPath.lastIndexOf(':')
        val projectPath = if (lastColon == 0) ":" else taskPath.substring(0, lastColon)
        val taskName = taskPath.substring(lastColon + 1)
        val projectDir = parameters.projectPathToRelativeProjectDir.get()[projectPath] ?: return null
        return if (projectDir.isEmpty()) "build/test-results/$taskName" else "$projectDir/build/test-results/$taskName"
    }
}
