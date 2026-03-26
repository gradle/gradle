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
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.scan.config.fixtures.ApplyDevelocityPluginFixture
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.ToolingApiGradleExecutor
import org.gradle.util.GradleVersion
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
// Caused by https://github.com/gradle/gradle/issues/35572
@DoesNotSupportNonAsciiPaths
class AbstractAndroidProjectSmokeTest extends AbstractSmokeTest implements RunnerFactory {

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

    protected void setupCopyOfAndroidProject(TestFile targetDir) {
        copyRemoteProject("androidProject", targetDir)
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(targetDir.file("settings.gradle.kts"))
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug", *excludingCCIncompatibleTasks())
            .deprecations(AndroidProjectDeprecations) {
                expectMultiStringNotationDeprecation(agpVersion)
            }
            .build()
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildCachedLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug", *excludingCCIncompatibleTasks())
            .deprecations(AndroidProjectDeprecations) {
                expectMultiStringNotationDeprecationIf(agpVersion, GradleContextualExecuter.isNotConfigCache())
            }
            .build()
    }

    static class AndroidProjectDeprecations extends BaseDeprecations implements WithAndroidDeprecations {
        AndroidProjectDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }
    }

    protected SmokeTestGradleRunner runnerForLocation(File projectDir, String agpVersion, String... tasks) {
        List<String> runnerArgs = [
            "-DagpVersion=$agpVersion",
            "-DkotlinVersion=$kotlinVersion",
            "-DjavaVersion=${AGP_VERSIONS.getMinimumJavaVersionFor(agpVersion).majorVersion}",
            "-DbuildToolsVersion=${AGP_VERSIONS.getBuildToolsVersionFor(agpVersion)}",
            "-Dscan.tag.NowInAndroidSmokeTest",
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

        String protobufClassifier
        if (OperatingSystem.current().isWindows()) {
            protobufClassifier = "windows-x86_64"
        } else if (OperatingSystem.current().isLinux()) {
            protobufClassifier = "linux-x86_64"
        } else if (OperatingSystem.current().isMacOsX()) {
            protobufClassifier = "osx-aarch_64"
        } else {
            throw new UnsupportedOperationException("Unsupported operating system: ${OperatingSystem.current().name}")
        }
        runner.maybeExpectLegacyDeprecationWarning(
            "Declaring dependencies using multi-string notation has been deprecated. This will fail with an error in Gradle 10. Please use single-string notation instead: \"com.google.protobuf:protoc:4.29.2:${protobufClassifier}@exe\". Consult the upgrading guide for further information: https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_9.html#dependency_multi_string_notation"
        )

        runner
    }

    // license plugin is not compatible with CC: https://github.com/google/play-services-plugins/issues/246
    protected List<String> excludingCCIncompatibleTasks() {
        if (GradleContextualExecuter.isConfigCache()) {
            return ["-x", ":app:prodDebugOssLicensesTask", "-x", ":app:demoDebugOssLicensesTask"]
        } else {
            return []
        }
    }

    protected static boolean verify(BuildResult result, Map<String, TaskOutcome> outcomes, Map<String, TaskOutcome> flakyOutcomes = [:]) {
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
                TaskOutcome flakyOutcome = flakyOutcomes[taskName]
                if (taskOutcome != flakyOutcome) {
                    println "> Task '$taskName' was $taskOutcome but should have been $expectedOutcome"
                    allOutcomesMatched = false
                }
            }
        }
        return hasMatchingTasks && allOutcomesMatched
    }
}
