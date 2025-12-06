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

package org.gradle.integtests.fixtures.configurationcache

import junit.framework.AssertionFailedError
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.Matcher

import static org.junit.Assert.assertThrows

/**
 * A `ConfigurationCacheProblemsFixture` that supports `ExecutionResult`.
 */
final class ConfigurationCacheProblemsExecutionResultFixture extends ConfigurationCacheProblemsFixture {

    ConfigurationCacheProblemsExecutionResultFixture(File rootDir) {
        super(rootDir)
    }

    ConfigurationCacheReportFixture htmlReport(ExecutionResult result) {
        return super.htmlReport(result.output)
    }

    ConfigurationCacheReportFixture htmlReport(ExecutionFailure failure) {
        return super.htmlReport(failure.error)
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated use {@link #htmlReport(ExecutionResult)} or {@link #htmlReport(ExecutionFailure)}
     */
    @Override
    @Deprecated
    ConfigurationCacheReportFixture htmlReport(String output) {
        return super.htmlReport(output)
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasProblems(failure, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, failureDescriptionMatcherForProblems(spec))

        htmlReport(failure).assertContents(spec)
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasTooManyProblems(failure, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        HasConfigurationCacheProblemsSpec spec
    ) {
        assertNoProblemsSummary(failure.output)
        assertFailureDescription(failure, failureDescriptionMatcherForTooManyProblems(spec))

        htmlReport(failure).assertContents(spec)
    }

    void assertResultHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertResultHasProblems(result, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        HasConfigurationCacheProblemsSpec spec
    ) {
        if (spec.hasProblems()) {
            assertHasConsoleSummary(result.output, spec)
        } else {
            assertNoProblemsSummary(result.output)
        }

        // Don't let Groovy to pick up more precise overload, we don't want to use htmlReport(ExecutionFailure) here.
        htmlReport(result as ExecutionResult).assertContents(spec)
    }

    void assertResultConsoleSummaryHasNoProblems(ExecutionResult result) {
        assertThrows(AssertionFailedError) {
            extractSummary(result.output)
        }
    }

    void assertResultHasConsoleSummary(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertHasConsoleSummary(result.output, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    private static void assertFailureDescription(
        ExecutionFailure failure,
        Matcher<String> failureMatcher
    ) {
        failure.assertThatDescription(failureMatcher)
    }

}
