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
import org.gradle.internal.cc.impl.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest

abstract class AbstractIsolatedProjectsIntegrationTest extends AbstractConfigurationCacheOptInFeatureIntegrationTest {
    public static final String ENABLE_CLI = "-D${StartParameterBuildOptions.IsolatedProjectsOption.PROPERTY_NAME}=true"
    public static final String ENABLE_DIAGNOSTICS = "-D${StartParameterBuildOptions.IsolatedProjectsDiagnosticsOption.PROPERTY_NAME}=true"

    /**
     * Convenience constant for parameterizing IP-violation tests over every supported mode.
     * Use as {@code where: mode << ALL_MODES} or via Spock 2.4 {@code combined:} blocks.
     */
    public static final List<IsolatedProjectsMode> ALL_MODES = IsolatedProjectsMode.values().toList()

    final def fixture = new IsolatedProjectsFixture(this)

    void withIsolatedProjects(String... moreExecuterArgs) {
        executer.withArgument(ENABLE_CLI, *moreExecuterArgs)
    }

    void withIsolatedProjectsDiagnostics(String... moreExecuterArgs) {
        executer.withArguments(ENABLE_CLI, ENABLE_DIAGNOSTICS, *moreExecuterArgs)
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
     * Runs tasks under {@code mode} and expects failure. Pair with
     * {@link IsolatedProjectsFixture#assertIsolatedProjectsProblems} to exercise the same scenario
     * under both IP execution modes.
     */
    void isolatedProjectsFailsUsing(IsolatedProjectsMode mode, String... tasks) {
        switch (mode) {
            case IsolatedProjectsMode.DIAGNOSTICS:
                isolatedProjectsDiagnosticsFails(tasks)
                break
            case IsolatedProjectsMode.FAIL_FAST:
                isolatedProjectsFails(tasks)
                break
            default:
                throw new IllegalArgumentException("Unsupported IP mode: $mode")
        }
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
