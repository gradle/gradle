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

package org.gradle.smoketests

import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.scan.config.fixtures.GradleEnterprisePluginSettingsFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
import org.junit.Rule


class AbstractAndroidSantaTrackerSmokeTest extends AbstractSmokeTest {

    protected static final List<String> TESTED_AGP_VERSIONS = AGP_VERSIONS.getLatestsFromMinorPlusNightly("3.6")

    @Rule
    TestNameTestDirectoryProvider temporaryFolder
    TestFile homeDir

    def setup() {
        homeDir = temporaryFolder.createDir("test-kit-home")
    }

    def cleanup() {
        // The daemons started by test kit need to be killed, so no locked files are left behind.
        DaemonLogsAnalyzer.newAnalyzer(homeDir.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }

    protected void setupCopyOfSantaTracker(TestFile targetDir, String flavour, String agpVersion = null) {
        copyRemoteProject("santaTracker${flavour.capitalize()}", targetDir)
        GradleEnterprisePluginSettingsFixture.applyEnterprisePlugin(targetDir.file("settings.gradle"))
    }

    protected BuildResult buildLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug").build()
    }

    protected BuildResult cleanLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "clean").build()
    }

    private GradleRunner runnerForLocation(File projectDir, String agpVersion, String... tasks) {
        def runner = runner(*[["-DagpVersion=$agpVersion"], tasks].flatten())
            .withProjectDir(projectDir)
            .withTestKitDir(homeDir)
            .forwardOutput()
        if (AGP_VERSIONS.isAgpNightly(agpVersion)) {
            def init = AGP_VERSIONS.createAgpNightlyRepositoryInitScript()
            runner.withArguments([runner.arguments, ['-I', init.canonicalPath]].flatten())
        }
        return runner
    }

    protected static boolean verify(BuildResult result, Map<String, TaskOutcome> outcomes) {
        println "> Expecting ${outcomes.size()} tasks with outcomes:"
        outcomes.values().groupBy { it }.sort().forEach { outcome, instances -> println "> - $outcome: ${instances.size()}" }

        def outcomesWithMatchingTasks = outcomes.findAll { result.task(it.key) }
        def hasMatchingTasks = outcomesWithMatchingTasks.size() == outcomes.size() && outcomesWithMatchingTasks.size() == result.tasks.size()
        if (!hasMatchingTasks) {
            println "> Tasks missing:    " + (outcomes.findAll { !outcomesWithMatchingTasks.keySet().contains(it.key) })
            println "> Tasks in surplus: " + (result.tasks.findAll { !outcomesWithMatchingTasks.keySet().contains(it.path) })
            println "> Updated definitions:"
            result.tasks
                .toSorted { a, b -> a.path <=> b.path }
                .forEach { task ->
                    println "'${task.path}': ${task.outcome},"
                }
        }

        boolean allOutcomesMatched = true
        outcomesWithMatchingTasks.each { taskName, expectedOutcome ->
            def taskOutcome = result.task(taskName)?.outcome
            if (taskOutcome != expectedOutcome) {
                println "> Task '$taskName' was $taskOutcome but should have been $expectedOutcome"
                allOutcomesMatched = false
            }
        }
        return hasMatchingTasks && allOutcomesMatched
    }
}
