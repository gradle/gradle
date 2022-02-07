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
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.internal.scan.config.fixtures.ApplyGradleEnterprisePluginFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
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
        return runnerForLocation(projectDir, agpVersion, "assembleDebug")
            .deprecations(SantaTrackerDeprecations) {
                expectAllFileTreeForEmptySourcesDeprecationWarnings(agpVersion)
            }.build()
    }

    protected BuildResult buildLocationMaybeExpectingWorkerExecutorDeprecation(File location, String agpVersion) {
        return runnerForLocation(location, agpVersion,"assembleDebug")
            .deprecations(SantaTrackerDeprecations) {
                expectAllFileTreeForEmptySourcesDeprecationWarnings(agpVersion)
                expectAndroidWorkerExecutionSubmitDeprecationWarning(agpVersion)
            }.build()
    }

    static class SantaTrackerDeprecations extends BaseDeprecations implements WithAndroidDeprecations {
        SantaTrackerDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        void expectAllFileTreeForEmptySourcesDeprecationWarnings(String agpVersion) {
            expectAndroidFileTreeForEmptySourcesDeprecationWarnings(agpVersion, "sourceFiles", "sourceDirs", "inputFiles", "resources", "projectNativeLibs")
        }
    }

    protected BuildResult cleanLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "clean").build()
    }

    protected SmokeTestGradleRunner runnerForLocation(File projectDir, String agpVersion, String... tasks) {
        def runnerArgs = [["-DagpVersion=$agpVersion", "-DkotlinVersion=$kotlinVersion", "--stacktrace"], tasks].flatten()
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_16)) {
            // fall back to using Java 11 (LTS) for Android tests
            // Kapt is not compatible with JDK 16+, https://youtrack.jetbrains.com/issue/KT-45545,
            // or Java 17 for now: https://youtrack.jetbrains.com/issue/KT-47583
            // perhaps we should always run Android tests on Java 11 instead of having some of them skipped by a precondition
            def jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_11)
            runnerArgs += "-Dorg.gradle.java.home=${jdk.javaHome}"
        }
        def runner = runner(*runnerArgs)
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
