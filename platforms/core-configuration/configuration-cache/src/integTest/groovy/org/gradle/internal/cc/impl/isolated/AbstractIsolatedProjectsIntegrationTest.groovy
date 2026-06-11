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

package org.gradle.internal.cc.impl.isolated

import org.gradle.initialization.StartParameterBuildOptions
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.internal.cc.impl.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest

abstract class AbstractIsolatedProjectsIntegrationTest extends AbstractConfigurationCacheOptInFeatureIntegrationTest {
    public static final String ENABLE_CLI = "-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true"
    public static final String ENABLE_DIAGNOSTICS = "-D${StartParameterBuildOptions.IsolatedProjectsDiagnosticsOption.PROPERTY_NAME}=true"
    public static final String ENABLE_DANGEROUSLY_IGNORE_PROBLEMS = "-D${StartParameterBuildOptions.IsolatedProjectsDangerouslyIgnoreProblemsOption.PROPERTY_NAME}=true"

    /**
     * Stable substring of the banner shown when IP problems are dangerously ignored.
     * Matches the banner in {@code ConfigurationCacheProblems}.
     */
    public static final String DANGEROUSLY_IGNORE_PROBLEMS_BANNER = "dangerously-ignore-problems is ENABLED"

    /**
     * Convenience constant for parameterizing IP-violation tests over every supported mode.
     * Use as {@code where: mode << ALL_MODES} or via Spock 2.4 {@code combined:} blocks.
     */
    public static final List<IsolatedProjectsMode> ALL_MODES = IsolatedProjectsMode.values().toList()

    final def fixture = new IsolatedProjectsFixture(this)

    void withIsolatedProjects(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI, *moreExecuterArgs)
    }

    void withIsolatedProjectsDiagnostics(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI, ENABLE_DIAGNOSTICS, *moreExecuterArgs)
    }

    void withIsolatedProjectsDangerouslyIgnoreProblems(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI, ENABLE_DANGEROUSLY_IGNORE_PROBLEMS, *moreExecuterArgs)
    }

    /**
     * Runs tasks with IP problems dangerously ignored, expecting the build to succeed despite any
     * IP violations. Unlike {@link #isolatedProjectsFails}, this is the only IP mode that succeeds
     * in the presence of violations.
     */
    void isolatedProjectsSucceedsIgnoringProblems(String... tasks) {
        run(ENABLE_CLI, ENABLE_DANGEROUSLY_IGNORE_PROBLEMS, *tasks)
    }

    /**
     * Asserts the dangerously-ignore-problems banner brackets the most recent build:
     *
     * <ul>
     *     <li>at the start: in the build output and <em>before</em> {@code firstBuildOutputMarker}, so the
     *     user sees the warning as close to the top as possible;</li>
     *     <li>again at the end, somewhere after {@code firstBuildOutputMarker} in the build output on a
     *     failed build or in the post-build output on a successful build</li>
     * </ul>
     *
     * @param firstBuildOutputMarker the first grouped build-output line the start banner must precede
     */
    void assertIgnoreProblemsBannerShownAtStartAndEnd(String firstBuildOutputMarker) {
        def buildOutput = result.normalizedOutput
        int bannerAt = buildOutput.indexOf(DANGEROUSLY_IGNORE_PROBLEMS_BANNER)
        int markerAt = buildOutput.indexOf(firstBuildOutputMarker)
        assert bannerAt >= 0: "expected the start banner in the build output"
        assert markerAt >= 0: "expected '$firstBuildOutputMarker' in the build output"
        assert bannerAt < markerAt: "expected the start banner before '$firstBuildOutputMarker'"
        if (result instanceof ExecutionFailure) {
            // beforeComplete output is flushed into the main output before the failure report on a failed build,
            // so the end banner appears a second time after the build output rather than in post-build output.
            assert buildOutput.lastIndexOf(DANGEROUSLY_IGNORE_PROBLEMS_BANNER) > markerAt:
                "expected the end banner after the output marker '$firstBuildOutputMarker' on a failed build"
        } else {
            result.assertHasPostBuildOutput(DANGEROUSLY_IGNORE_PROBLEMS_BANNER)
        }
    }

    void isolatedProjectsRun(String... tasks) {
        run(ENABLE_CLI, *tasks)
    }

    void isolatedProjectsFails(String... tasks) {
        fails(ENABLE_CLI, *tasks)
    }

    void isolatedProjectsDiagnosticsFails(String... tasks) {
        fails(ENABLE_CLI, ENABLE_DIAGNOSTICS, *tasks)
    }

    /**
     * Applies the executer arguments for the given IP {@code mode} without invoking a build, leaving
     * the invocation to the caller. Use when the build is not a plain CLI run, e.g. a Tooling API
     * build action, to exercise the same scenario under both IP execution modes.
     */
    void withIsolatedProjectsUsing(IsolatedProjectsMode mode, String... moreExecuterArgs) {
        switch (mode) {
            case IsolatedProjectsMode.DIAGNOSTICS:
                withIsolatedProjectsDiagnostics(moreExecuterArgs)
                break
            case IsolatedProjectsMode.FAIL_FAST:
                withIsolatedProjects(moreExecuterArgs)
                break
            default:
                throw new IllegalArgumentException("Unsupported IP mode: $mode")
        }
    }

    /**
     * Runs tasks under {@code mode} and expects failure. Pair with
     * {@link IsolatedProjectsFixture#assertIsolatedProjectsProblems} to exercise the same scenario
     * under both IP execution modes.
     */
    void isolatedProjectsFailsUsing(IsolatedProjectsMode mode, String... tasks) {
        withIsolatedProjectsUsing(mode)
        fails(tasks)
    }

    /**
     * The IP execution mode under test.
     *
     * <p>{@link #DIAGNOSTICS} configures projects sequentially and collects every violation
     * as a deferred CC problem; the cache entry is stored and then discarded.
     *
     * <p>{@link #FAIL_FAST} (default) configures projects in optimistic parallel and the
     * first violation throws, halting configuration before the cache is written.
     */
    enum IsolatedProjectsMode {
        DIAGNOSTICS,
        FAIL_FAST
    }
}
