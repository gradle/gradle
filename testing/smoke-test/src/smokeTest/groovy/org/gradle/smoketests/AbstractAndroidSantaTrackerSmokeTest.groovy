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
import org.gradle.internal.scan.config.fixtures.ApplyDevelocityPluginFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
import org.junit.Rule

/**
 * For these tests to run you need to set ANDROID_SDK_ROOT to your Android SDK directory
 *
 * https://developer.android.com/studio/releases/build-tools.html
 * https://developer.android.com/studio/releases/gradle-plugin.html
 * https://androidstudio.googleblog.com/
 *
 * To run your tests against all AGP versions from agp-versions.properties, use higher version of java by setting -PtestJavaVersion=<version>
 * See {@link org.gradle.integtests.fixtures.versions.AndroidGradlePluginVersions#assumeCurrentJavaVersionIsSupportedBy() assumeCurrentJavaVersionIsSupportedBy} for more details
 */
class AbstractAndroidSantaTrackerSmokeTest extends AbstractSmokeTest implements RunnerFactory {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    TestFile homeDir

    String kotlinVersion = KOTLIN_VERSIONS.latestStable

    def setup() {
        homeDir = temporaryFolder.createDir("test-kit-home")
    }

    def cleanup() {
        // The daemons started by test kit need to be killed, so no locked files are left behind.
        DaemonLogsAnalyzer.newAnalyzer(homeDir.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }

    protected void setupCopyOfSantaTracker(TestFile targetDir) {
        copyRemoteProject("santaTracker", targetDir)
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(targetDir.file("settings.gradle"))
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug").deprecations(SantaTrackerDeprecations) {
            expectIsPropertyDeprecationWarnings()
        }.build()
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildCachedLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug").build()
    }

    static class SantaTrackerDeprecations extends BaseDeprecations implements WithAndroidDeprecations {
        SantaTrackerDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }
    }

    protected SmokeTestGradleRunner runnerForLocation(File projectDir, String agpVersion, String... tasks) {
        List<String> runnerArgs = [
            // TODO: the versions of KGP we use still access Task.project from a cacheIf predicate
            // A workaround for this has been added to TaskExecutionAccessCheckers;
            // TODO once we remove it, uncomment the flag below or upgrade AGP
            // "-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true",
            "-DagpVersion=$agpVersion",
            "-DkotlinVersion=$kotlinVersion",
            "-DjavaVersion=${AGP_VERSIONS.getMinimumJavaVersionFor(agpVersion).majorVersion}",
            "-DbuildToolsVersion=${AGP_VERSIONS.getBuildToolsVersionFor(agpVersion)}",
            "--stacktrace"
        ] + tasks.toList()

        def runner = agpRunner(agpVersion, *runnerArgs)
            .withProjectDir(projectDir)
            .withTestKitDir(homeDir)
            .withJdkWarningChecksDisabled() // Kapt seems to be accessing JDK internals. See KT-49187

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
        runner
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
