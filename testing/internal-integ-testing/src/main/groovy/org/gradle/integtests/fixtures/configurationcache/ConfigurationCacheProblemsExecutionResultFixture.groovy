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

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        @DelegatesTo(value = HasConfigurationCacheErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasError(failure, newErrorSpec(error, ConfigureUtil.configureUsing(specClosure)))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        HasConfigurationCacheErrorSpec spec
    ) {
        assertOutputHasError(failure.output, spec)
        assertFailureDescription(failure, failureDescriptionMatcherForError(spec))
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
        assertProblemsHtmlReport(failure.error, rootDir, spec)
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
        assertProblemsHtmlReport(failure.error, rootDir, spec)
    }

    void assertResultHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure = {}
    ) {
        assertResultHasProblems(result, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        HasConfigurationCacheProblemsSpec spec
    ) {
        if (spec.hasProblems()) {
            assertHasConsoleSummary(result.output, spec)
            assertProblemsHtmlReport(result.output, rootDir, spec)
        } else {
            assertNoProblemsSummary(result.output)
        }
        // TODO:bamboo avoid reading jsModel more than once when asserting on problems AND inputs AND incompatible tasks
        assertInputs(result.output, rootDir, spec)
        assertIncompatibleTasks(result.output, rootDir, spec)
    }

    void assertFailureHtmlReportHasProblems(
        ExecutionFailure failure,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertHtmlReportHasProblems(failure.error, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
    }

    void assertResultHtmlReportHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertHtmlReportHasProblems(result.output, newProblemsSpec(ConfigureUtil.configureUsing(specClosure)))
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
