/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions

@Requires([
    TestExecutionPreconditions.NotConfigCached,
])
abstract class AbstractGradleceptionSmokeTest extends AbstractSmokeTest {

    /**
     * The version the nested build stamps on its own output. A wall-clock value changes on every test run,
     * which invalidates the build cache entry of every task that embeds the version - the build receipt, the
     * jars, the distribution zips. Pin it so those stay cacheable across runs.
     *
     * This is deliberately different from the timestamp the CI build configuration gives the distribution
     * that *runs* these nested builds (see {@code SmokeTests.kt}), matching how Gradleception distinguishes
     * its two dogfooding distributions. The version of the running distribution - not this one - is what
     * determines the cache keys of the bulk of the nested build's tasks.
     */
    public static final String FIXED_BUILD_TIMESTAMP = "19800202020202+0000"
    public static final String TEST_BUILD_TIMESTAMP = "-PbuildTimestamp=" + FIXED_BUILD_TIMESTAMP
    private static final String DISABLE_IP = "-Dorg.gradle.isolated-projects=false"
    // The nested build is a fresh checkout with no incoming-distributions/, but be explicit: an incoming
    // build receipt would silently win over TEST_BUILD_TIMESTAMP (see BuildTimestampValueSource).
    private static final String IGNORE_INCOMING_BUILD_RECEIPT = "-PignoreIncomingBuildReceipt=true"
    private static final List<String> GRADLE_BUILD_TEST_ARGS = [DISABLE_IP, TEST_BUILD_TIMESTAMP, IGNORE_INCOMING_BUILD_RECEIPT]

    private SmokeTestGradleRunner.SmokeTestBuildResult result

    def setup() {
        new TestFile("build/gradleBuildCurrent").copyTo(testProjectDir)

        and:
        // Forward all known JDK installations so the inner gradle/gradle build can locate the daemon toolchain it requires (gradle-daemon-jvm.properties).
        def installationPaths = AvailableJavaHomes.availableJvms.collect { it.javaHome.absolutePath.replace("\\", "/") }.join(",")
        file("gradle.properties") << "\norg.gradle.java.installations.paths=${installationPaths}\n"
    }

    SmokeTestGradleRunner.SmokeTestBuildResult getResult() {
        if (result == null) {
            throw new IllegalStateException("Need to run a build before result is available.")
        }
        return result
    }

    protected void run(List<String> tasks, File testKitDir = null) {
        run(runnerFor(tasks, testKitDir))
    }

    protected void run(SmokeTestGradleRunner runner) {
        result = null
        result = runner.build()
    }

    protected void fails(List<String> tasks, File testKitDir = null) {
        fails(runnerFor(tasks, testKitDir))
    }

    protected void fails(SmokeTestGradleRunner runner) {
        result = null
        result = runner.buildAndFail()
    }

    SmokeTestGradleRunner runner(String... tasks) {
        List<String> args = GRADLE_BUILD_TEST_ARGS + (tasks as List<String>);
        return super.runner(*args)
    }

    protected SmokeTestGradleRunner runnerFor(List<String> tasks, File testKitDir) {
        def runner = testKitDir != null
            ? runnerWithTestKitDir(testKitDir, tasks)
            : runner(*tasks)

        runner.ignoreDeprecationWarnings("Gradleception smoke tests don't check for deprecation warnings; TODO: we should add expected deprecations for each task being called")
        runner.withJdkWarningChecksDisabled() // The Gradle build somehow still emits these warnings
        // The Gradle build publishes Build Scans and uses the remote build cache, so a Develocity outage makes the
        // Develocity agent log connection stack traces to the build output. Those are infrastructure noise unrelated
        // to what these smoke tests verify. See https://github.com/gradle/gradle-private/issues/5290
        runner.ignoreStackTraces("Develocity agent may log stack traces when ge.gradle.org / the remote build cache is unavailable")

        return runner
    }

    private SmokeTestGradleRunner runnerWithTestKitDir(File testKitDir, List<String> gradleArgs) {
        runnerWithGradleUserHome(IntegrationTestBuildContext.INSTANCE.gradleUserHomeDir, *(gradleArgs as String[]))
            .withTestKitDir(testKitDir)
    }
}
