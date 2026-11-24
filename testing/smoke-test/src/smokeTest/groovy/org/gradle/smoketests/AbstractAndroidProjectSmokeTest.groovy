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
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.scan.config.fixtures.ApplyDevelocityPluginFixture
import org.gradle.test.fixtures.file.DoesNotSupportNonAsciiPaths
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
// Caused by https://github.com/gradle/gradle/issues/35572
@DoesNotSupportNonAsciiPaths
class AbstractAndroidProjectSmokeTest extends AbstractSmokeTest implements RunnerFactory {

    @Rule
    TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    // Use the Gradle user home shared by the whole build's integration tests instead of a fresh TestKit home per
    // test. A fresh home forced every test method to re-download the full Android/Kotlin dependency set (~1GB)
    // from the mirror. These tests run serially (maxParallelForks = 1), so sharing the home - and reusing its
    // daemons across methods - is safe.
    static final TestFile SHARED_GRADLE_USER_HOME = IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir

    String kotlinVersion = KOTLIN_VERSIONS.latestStable

    def cleanupSpec() {
        // The daemons started by test kit need to be killed, so no locked files are left behind.
        DaemonLogsAnalyzer.newAnalyzer(SHARED_GRADLE_USER_HOME.file(ToolingApiGradleExecutor.TEST_KIT_DAEMON_DIR_NAME)).killAll()
    }

    protected void setupCopyOfAndroidProject(TestFile targetDir) {
        copyRemoteProject("androidProject", targetDir)
        ApplyDevelocityPluginFixture.applyDevelocityPlugin(targetDir.file("settings.gradle.kts"))
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug", *excludingCCIncompatibleTasks())
            .deprecations(AndroidProjectDeprecations) {
                expectProjectDependencyNotationDeprecation()
                expectSetVisibleDeprecation()
                expectNowInAndroidArtifactlessPrimaryVariantDeprecationsIf(true)
            }
            .build()
    }

    protected SmokeTestGradleRunner.SmokeTestBuildResult buildCachedLocation(File projectDir, String agpVersion) {
        return runnerForLocation(projectDir, agpVersion, "assembleDebug", *excludingCCIncompatibleTasks())
            .deprecations(AndroidProjectDeprecations) {
                expectProjectDependencyNotationDeprecationIf(GradleContextualExecuter.isNotConfigCache())
                expectSetVisibleDeprecationIf(GradleContextualExecuter.isNotConfigCache())
                expectNowInAndroidArtifactlessPrimaryVariantDeprecationsIf(GradleContextualExecuter.isNotConfigCache())
            }
            .build()
    }

    static class AndroidProjectDeprecations extends BaseDeprecations implements WithAndroidDeprecations {
        private static final List<String> ALL_DEBUG_ELEMENTS = ['demoDebugApiElements', 'demoDebugRuntimeElements', 'prodDebugApiElements', 'prodDebugRuntimeElements']
        private static final List<String> DEBUG_RUNTIME_ELEMENTS = ['demoDebugRuntimeElements', 'prodDebugRuntimeElements']

        /**
         * The configurations of each library project whose primary variant has no artifacts, and which AGP would select
         * with the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
         */
        private static final Map<String, List<String>> CONFIGURATIONS_WITHOUT_ARTIFACTS = [
            ':core:analytics': ALL_DEBUG_ELEMENTS,
            ':core:data': ALL_DEBUG_ELEMENTS,
            ':core:database': ALL_DEBUG_ELEMENTS,
            ':core:datastore': ALL_DEBUG_ELEMENTS,
            ':core:designsystem': ALL_DEBUG_ELEMENTS,
            ':core:domain': DEBUG_RUNTIME_ELEMENTS,
            ':core:navigation': ALL_DEBUG_ELEMENTS,
            ':core:network': ALL_DEBUG_ELEMENTS,
            ':core:notifications': DEBUG_RUNTIME_ELEMENTS,
            ':core:ui': ALL_DEBUG_ELEMENTS,
            ':feature:bookmarks:api': ALL_DEBUG_ELEMENTS,
            ':feature:bookmarks:impl': ALL_DEBUG_ELEMENTS,
            ':feature:foryou:api': ALL_DEBUG_ELEMENTS,
            ':feature:foryou:impl': ALL_DEBUG_ELEMENTS,
            ':feature:interests:api': ALL_DEBUG_ELEMENTS,
            ':feature:interests:impl': ALL_DEBUG_ELEMENTS,
            ':feature:search:api': ALL_DEBUG_ELEMENTS,
            ':feature:search:impl': ALL_DEBUG_ELEMENTS,
            ':feature:settings:impl': ALL_DEBUG_ELEMENTS,
            ':feature:topic:api': ALL_DEBUG_ELEMENTS,
            ':feature:topic:impl': ALL_DEBUG_ELEMENTS,
            ':sync:work': ALL_DEBUG_ELEMENTS,
            ':ui-test-hilt-manifest': ALL_DEBUG_ELEMENTS
        ]

        private static final List<String> DEMO_DEBUG_ELEMENTS = ['demoDebugApiElements', 'demoDebugRuntimeElements']

        /**
         * The further demo configurations whose primary variant has no artifacts, and which linting the app would select
         * with the {@code CONSIDER_ARTIFACTLESS_PRIMARY_VARIANTS} feature preview.
         */
        private static final Map<String, List<String>> CONFIGURATIONS_WITHOUT_ARTIFACTS_FOR_LINT = [
            ':core:data-test': DEMO_DEBUG_ELEMENTS,
            ':core:datastore-test': DEMO_DEBUG_ELEMENTS,
            ':core:domain': ['demoDebugApiElements'],
            ':core:notifications': ['demoDebugApiElements'],
            ':core:screenshot-testing': DEMO_DEBUG_ELEMENTS,
            ':core:testing': DEMO_DEBUG_ELEMENTS,
            ':sync:sync-test': DEMO_DEBUG_ELEMENTS
        ]

        AndroidProjectDeprecations(SmokeTestGradleRunner runner) {
            super(runner)
        }

        /**
         * Expect the deprecations for the primary variants without artifacts that linting the app ignores.
         * Linting only resolves the demo flavor, so it ignores the demo configurations that building ignores, and some more.
         */
        void expectNowInAndroidLintArtifactlessPrimaryVariantDeprecations() {
            CONFIGURATIONS_WITHOUT_ARTIFACTS.each { project, configurations ->
                def demoConfigurations = configurations.findAll { it.startsWith('demo') }
                expectArtifactlessPrimaryVariantDeprecations(project, demoConfigurations)
                expectArtifactlessPrimaryVariantDeprecations(project, demoConfigurations, ['org.gradle.libraryelements=jar'])
            }
            CONFIGURATIONS_WITHOUT_ARTIFACTS_FOR_LINT.each { project, configurations ->
                expectArtifactlessPrimaryVariantDeprecations(project, configurations)
            }
        }

        /**
         * Expect the deprecations for the library projects' primary variants without artifacts that building the app ignores.
         * <p>
         * Two requests ignore each of them: one that a single secondary variant matches, so the deprecation suggests an attribute,
         * and one that it does not. For the API elements, the latter is lenient and nothing else matches it, so it resolves to
         * no artifacts either way, and emits no deprecation.
         */
        void expectNowInAndroidArtifactlessPrimaryVariantDeprecationsIf(boolean condition) {
            if (condition) {
                CONFIGURATIONS_WITHOUT_ARTIFACTS.each { project, configurations ->
                    expectArtifactlessPrimaryVariantDeprecations(project, configurations.findAll { !it.endsWith('ApiElements') })
                    expectArtifactlessPrimaryVariantDeprecations(project, configurations, ['org.gradle.libraryelements=jar'])
                }
            }
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
            .withTestKitDir(SHARED_GRADLE_USER_HOME)
            .withJdkWarningChecksDisabled() // Kapt seems to be accessing JDK internals. See KT-49187
            // These projects apply the Develocity plugin and use the remote build cache, so a Develocity outage makes
            // the Develocity agent log connection stack traces to the build output. Those are infrastructure noise
            // unrelated to what these smoke tests verify. See https://github.com/gradle/gradle-private/issues/5290
            .ignoreStackTraces("Develocity agent may log stack traces when develocity.gradle.org / the remote build cache is unavailable")

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
