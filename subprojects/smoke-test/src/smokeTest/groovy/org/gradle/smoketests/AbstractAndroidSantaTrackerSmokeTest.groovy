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

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
import org.gradle.util.GradleVersion
import org.junit.Rule

class AbstractAndroidSantaTrackerSmokeTest extends AbstractSmokeTest {

    protected static final Iterable<String> TESTED_AGP_VERSIONS = TestedVersions.androidGradle.versions

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    TestFile homeDir

    String kotlinVersion = TestedVersions.kotlin.latest()

    def setup() {
        homeDir = temporaryFolder.createDir("test-kit-home")
    }

    def cleanup() {
        // The daemons started by test kit need to be killed, so no locked files are left behind.
        DaemonLogsAnalyzer.newAnalyzer(homeDir.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }

    protected void setupCopyOfSantaTracker(TestFile targetDir) {
        copyRemoteProject("santaTracker", targetDir)
        ApplyGradleEnterprisePluginFixture.applyEnterprisePlugin(targetDir.file("settings.gradle"))
    }

    protected BuildResult buildLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug").build()
    }

    protected BuildResult buildLocationMaybeExpectingWorkerExecutorDeprecation(File location, String agpVersion) {
        return runnerForLocationMaybeExpectingWorkerExecutorDeprecation(location, agpVersion, "assembleDebug")
            .build()
    }

    protected SmokeTestGradleRunner runnerForLocationMaybeExpectingWorkerExecutorDeprecation(File location, String agpVersion, String... tasks) {
        return runnerForLocation(location, agpVersion, tasks)
            .expectLegacyDeprecationWarningIf(agpVersion.startsWith("4.1"),
                "The WorkerExecutor.submit() method has been deprecated. " +
                    "This is scheduled to be removed in Gradle 8.0. " +
                    "Please use the noIsolation(), classLoaderIsolation() or processIsolation() method instead. " +
                    "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_5.html#method_workerexecutor_submit_is_deprecated for more details."
            )
    }

    protected BuildResult cleanLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "clean").build()
    }

    protected SmokeTestGradleRunner runnerForLocation(File projectDir, String agpVersion, String... tasks) {
        def runner = runner(*[["-DagpVersion=$agpVersion", "-DkotlinVersion=$kotlinVersion", "--stacktrace"], tasks].flatten())
            .withProjectDir(projectDir)
            .withTestKitDir(homeDir)
            .forwardOutput()
        if (JavaVersion.current().isJava9Compatible()) {
            runner.withJvmArguments(
                "-Xmx8g", "-XX:MaxMetaspaceSize=1024m", "-XX:+HeapDumpOnOutOfMemoryError",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.jvm=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                "--add-opens", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED"
            )
        }
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
