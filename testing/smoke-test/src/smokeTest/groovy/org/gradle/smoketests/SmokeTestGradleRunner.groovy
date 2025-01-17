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

import org.gradle.integtests.fixtures.BuildOperationTreeFixture
import org.gradle.integtests.fixtures.BuildOperationTreeQueries
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.ExpectedDeprecationWarning
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.ResultAssertion
import org.gradle.internal.operations.trace.BuildOperationTrace
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.BuildTask
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.InvalidPluginMetadataException
import org.gradle.testkit.runner.InvalidRunnerConfigurationException
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.gradle.util.GradleVersion
import org.slf4j.LoggerFactory

import javax.annotation.Nullable
import java.util.function.Function

class SmokeTestGradleRunner extends GradleRunner {
    private static final LOGGER = LoggerFactory.getLogger(SmokeTestGradleRunner)

    private final DefaultGradleRunner delegate

    private final List<String> expectedDeprecationWarnings = []
    private final List<String> maybeExpectedDeprecationWarnings = []
    private boolean ignoreDeprecationWarnings
    private boolean jdkWarningChecksOn = true

    SmokeTestGradleRunner(
        IntegrationTestBuildContext context,
        List<String> args,
        List<String> jvmArgs,
        File projectDir
    ) {
        this.delegate = GradleRunner.create() as DefaultGradleRunner

        delegate.withGradleInstallation(context.gradleHomeDir)
        delegate.withTestKitDir(context.gradleUserHomeDir)
        delegate.withProjectDir(projectDir)
        delegate.forwardOutput()
        delegate.withArguments(args)
        delegate.withJvmArguments(jvmArgs)
    }

    @Override
    SmokeTestBuildResult build() {
        execute(GradleRunner::build)
    }

    @Override
    SmokeTestBuildResult buildAndFail() {
        execute(GradleRunner::buildAndFail)
    }

    @Override
    SmokeTestBuildResult run() throws InvalidRunnerConfigurationException {
        execute(GradleRunner::run)
    }

    private SmokeTestBuildResult execute(Function<GradleRunner, BuildResult> action) {
        String buildOperationTracePath = new File(projectDir, "operations").absolutePath
        doEnableBuildOperationTracing(buildOperationTracePath)

        def result = action.apply(delegate)
        verifyDeprecationWarnings(result)
        def operations = new BuildOperationTreeFixture(BuildOperationTrace.readPartialTree(buildOperationTracePath))
        new SmokeTestBuildResult(result, operations)
    }

    private void doEnableBuildOperationTracing(String buildOperationTracePath) {
        // TODO: Should we filter using the stable/public build operation class names?
        // This means we need to load classes and do an instanceof when we filter
        String buildOperationFilter = [
            "org.gradle.configurationcache.WorkGraphStoreDetails",
            "org.gradle.configurationcache.WorkGraphLoadDetails",
        ].join(BuildOperationTrace.FILTER_SEPARATOR)

        delegate.withArguments(delegate.getArguments() + [
            "-D${BuildOperationTrace.SYSPROP}=${buildOperationTracePath}".toString(),
            "-D${BuildOperationTrace.FILTER_SYSPROP}=${buildOperationFilter}".toString()
        ])
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called.
     *
     * @param warning the text of the warning to match.
     * @param followup how are we planning to resolve the deprecation before it turns into a breakage;
     *      typically a URL pointing to an issue with the relevant third-party plugin. The actual value
     *      is ignored, the parameter is only present to remind us that a followup is necessary, and
     *      to record how it will happen.
     */
    SmokeTestGradleRunner expectDeprecationWarning(String warning, String followup) {
        if (followup == null || followup.isBlank()) {
            throw new IllegalArgumentException("Follow up is required! Did you mean to expect a legacy deprecation warning instead?")
        }
        expectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called if the given condition is true.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     * @param followup how are we planning to resolve the deprecation before it turns into a breakage;
     *      typically a URL pointing to an issue with the relevant third-party plugin. The actual value
     *      is ignored, the parameter is only present to remind us that a followup is necessary, and
     *      to record how it will happen.
     */
    SmokeTestGradleRunner expectDeprecationWarningIf(boolean condition, String warning, String followup) {
        if (condition) {
            expectDeprecationWarning(warning, followup)
        }
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin. The assumption is that the deprecation has already
     * been fixed in a later version of the plugin, and thus no followup is needed.
     *
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner expectLegacyDeprecationWarning(String warning) {
        expectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin if the given condition is true.
     * The assumption is that the deprecation has already been fixed in a later version of the plugin,
     * and thus no followup is needed.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner expectLegacyDeprecationWarningIf(boolean condition, String warning) {
        if (condition) {
            expectLegacyDeprecationWarning(warning)
        }
        return this
    }

    /**
     * Maybe expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin. The assumption is that the deprecation has already
     * been fixed in a later version of the plugin, and thus no followup is needed.
     *
     * Does not fail the test if the warning does not appear in the output.
     *
     * WARNING: Only use for warnings that occurs intermittently. For example a deprecation warning for a function
     * that is only called once per Gradle daemon from a third party plugin.
     *
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner maybeExpectLegacyDeprecationWarning(String warning) {
        maybeExpectedDeprecationWarnings.add(warning)
        return this
    }

    /**
     * Maybe expect a deprecation warning to appear when {@link #build()} or {@link #buildAndFail()} is called
     * for an old version of a third-party plugin if the given condition is true.
     * The assumption is that the deprecation has already been fixed in a later version of the plugin,
     * and thus no followup is needed.
     *
     * Does not fail the test if the warning does not appear in the output.
     *
     * WARNING: Only use for warnings that occurs intermittently. For example a deprecation warning for a function
     * that is only called once per Gradle daemon from a third party plugin.
     *
     * @param condition only expect the warning to be produced when this condition is {@code true}.
     * @param warning the text of the warning to match.
     */
    SmokeTestGradleRunner maybeExpectLegacyDeprecationWarningIf(boolean condition, String warning) {
        if (condition) {
            maybeExpectLegacyDeprecationWarning(warning)
        }
        return this
    }

    SmokeTestGradleRunner ignoreDeprecationWarnings(String reason) {
        LOGGER.warn("Ignoring deprecation warnings because: {}", reason)
        ignoreDeprecationWarnings = true
        return this
    }

    SmokeTestGradleRunner ignoreDeprecationWarningsIf(boolean condition, String reason) {
        if (condition) {
            ignoreDeprecationWarnings(reason)
        }
        return this
    }

    /**
     * Disables checks for warnings emitted by the JDK itself. Including illegal access warnings.
     */
    SmokeTestGradleRunner withJdkWarningChecksDisabled() {
        this.jdkWarningChecksOn = false
        return this
    }

    def <U extends BaseDeprecations, T> SmokeTestGradleRunner deprecations(
        @DelegatesTo.Target Class<U> deprecationClass,
        @DelegatesTo(
            genericTypeIndex = 0,
            strategy = Closure.DELEGATE_FIRST)
            Closure<T> closure) {
        deprecationClass.newInstance(this).tap(closure)
        return this
    }

    def <T> SmokeTestGradleRunner deprecations(
        @DelegatesTo(
            value = BaseDeprecations.class,
            strategy = Closure.DELEGATE_FIRST)
            Closure<T> closure) {
        return deprecations(BaseDeprecations, closure)
    }

    private void verifyDeprecationWarnings(BuildResult result) {
        // TODO: Use problems API to verify deprecation warnings instead of parsing output.
        ExecutionResult execResult = OutputScrapingExecutionResult.from(result.output, "")

        maybeExpectedDeprecationWarnings.add(
            "Executing Gradle on JVM versions 16 and lower has been deprecated. " +
                "This will fail with an error in Gradle 9.0. " +
                "Use JVM 17 or greater to execute Gradle. " +
                "Projects can continue to use older JVM versions via toolchains. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/${GradleVersion.current().version}/userguide/upgrading_version_8.html#minimum_daemon_jvm_version"
        )

        List<String> deprecationWarningsToCheck = []
        if (!ignoreDeprecationWarnings) {
            deprecationWarningsToCheck = expectedDeprecationWarnings
        }

        new ResultAssertion(
            0,
            deprecationWarningsToCheck.collect { ExpectedDeprecationWarning.withMessage(it) },
            maybeExpectedDeprecationWarnings.collect { ExpectedDeprecationWarning.withMessage(it) },
            false,
            !ignoreDeprecationWarnings,
            jdkWarningChecksOn
        ).execute(execResult)

        expectedDeprecationWarnings.clear()
        maybeExpectedDeprecationWarnings.clear()
    }

    @Override
    SmokeTestGradleRunner withGradleVersion(String versionNumber) {
        throw new UnsupportedOperationException("Smoke tests should only run against the current Gradle version. Use cross version tests to test other Gradle versions.")
    }

    @Override
    SmokeTestGradleRunner withGradleInstallation(File installation) {
        throw new UnsupportedOperationException("Smoke tests should only run against the current Gradle version. Use cross version tests to test other Gradle versions.")
    }

    @Override
    SmokeTestGradleRunner withGradleDistribution(URI distribution) {
        throw new UnsupportedOperationException("Smoke tests should only run against the current Gradle version. Use cross version tests to test other Gradle versions.")
    }

    @Override
    SmokeTestGradleRunner withTestKitDir(File testKitDir) {
        delegate.withTestKitDir(testKitDir)
        return this
    }

    @Override
    File getProjectDir() {
        return delegate.getProjectDir()
    }

    @Override
    SmokeTestGradleRunner withProjectDir(File projectDir) {
        delegate.withProjectDir(projectDir)
        return this
    }

    @Override
    List<String> getArguments() {
        return delegate.getArguments()
    }

    @Override
    SmokeTestGradleRunner withArguments(List<String> arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    SmokeTestGradleRunner withArguments(String... arguments) {
        delegate.withArguments(arguments)
        return this
    }

    @Override
    List<? extends File> getPluginClasspath() {
        return delegate.getPluginClasspath()
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath() throws InvalidPluginMetadataException {
        delegate.withPluginClasspath()
        return this
    }

    @Override
    SmokeTestGradleRunner withPluginClasspath(Iterable<? extends File> classpath) {
        delegate.withPluginClasspath(classpath)
        return this
    }

    @Override
    boolean isDebug() {
        return delegate.isDebug()
    }

    @Override
    SmokeTestGradleRunner withDebug(boolean flag) {
        delegate.withDebug(flag)
        return this
    }

    @Override
    @Nullable
    Map<String, String> getEnvironment() {
        return delegate.getEnvironment()
    }

    @Override
    SmokeTestGradleRunner withEnvironment(@Nullable Map<String, String> environmentVariables) {
        delegate.withEnvironment(environmentVariables)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdOutput(Writer writer) {
        delegate.forwardStdOutput(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardStdError(Writer writer) {
        delegate.forwardStdError(writer)
        return this
    }

    @Override
    SmokeTestGradleRunner forwardOutput() {
        delegate.forwardOutput()
        return this
    }

    List<String> getJvmArguments() {
        return delegate.getJvmArguments()
    }

    SmokeTestGradleRunner withJvmArguments(List<String> jvmArguments) {
        delegate.withJvmArguments(jvmArguments)
        return this
    }

    SmokeTestGradleRunner withJvmArguments(String... jvmArguments) {
        delegate.withJvmArguments(Arrays.asList(jvmArguments))
        return this
    }

    class SmokeTestBuildResult implements BuildResult {

        private final BuildResult delegate
        private final BuildOperationTreeQueries operations

        SmokeTestBuildResult(BuildResult delegate, @Nullable BuildOperationTreeQueries operations) {
            this.delegate = delegate
            this.operations = operations
        }

        @Override
        String getOutput() {
            return delegate.output
        }

        @Override
        List<BuildTask> getTasks() {
            return delegate.tasks
        }

        @Override
        List<BuildTask> tasks(TaskOutcome outcome) {
            return delegate.tasks(outcome)
        }

        @Override
        List<String> taskPaths(TaskOutcome outcome) {
            return delegate.taskPaths(outcome)
        }

        @Override
        BuildTask task(String taskPath) {
            return delegate.task(taskPath)
        }

        void assertConfigurationCacheStateStored() {
            assertBuildOperationTracePresent()
            new ConfigurationCacheBuildOperationsFixture(operations).assertStateStored()
        }

        void assertConfigurationCacheStateLoaded() {
            assertBuildOperationTracePresent()
            new ConfigurationCacheBuildOperationsFixture(operations).assertStateLoaded()
        }

        private void assertBuildOperationTracePresent() {
            assert operations != null, "Build operation trace was not captured"
        }
    }
}
