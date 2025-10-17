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
import org.gradle.api.Action
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.util.internal.ConfigureUtil
import org.hamcrest.Matcher

import static org.junit.Assert.assertThrows

/**
 *
 */
final class ConfigurationCacheProblemsFixture extends ConfigurationCacheReportProblemsFixture {

    ConfigurationCacheProblemsFixture(File rootDir) {
        super(rootDir)
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        @DelegatesTo(value = HasConfigurationCacheErrorSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertFailureHasError(failure, error, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasError(
        ExecutionFailure failure,
        String error,
        Action<HasConfigurationCacheErrorSpec> specAction = {}
    ) {
        assertFailureHasError(failure, newErrorSpec(error, specAction))
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
        assertFailureHasProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasProblems(
        ExecutionFailure failure,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertFailureHasProblems(failure, newProblemsSpec(specAction))
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
        assertFailureHasTooManyProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHasTooManyProblems(
        ExecutionFailure failure,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertFailureHasTooManyProblems(failure, newProblemsSpec(specAction))
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
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertResultHasProblems(result, ConfigureUtil.configureUsing(specClosure))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertResultHasProblems(result, newProblemsSpec(specAction))
    }

    void assertResultHasProblems(
        ExecutionResult result,
        HasConfigurationCacheProblemsSpec spec
    ) {
        // assert !(result instanceof ExecutionFailure)
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
        assertFailureHtmlReportHasProblems(failure, ConfigureUtil.configureUsing(specClosure))
    }

    void assertFailureHtmlReportHasProblems(
        ExecutionFailure failure,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertHtmlReportHasProblems(failure.error, newProblemsSpec(specAction))
    }

    void assertResultHtmlReportHasProblems(
        ExecutionResult result,
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        assertResultHtmlReportHasProblems(result, ConfigureUtil.configureUsing(specClosure))
    }

    /**
     * Asserts a report generated for non-fatal
     * @param result
     * @param specAction
     */
    void assertResultHtmlReportHasProblems(
        ExecutionResult result,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertHtmlReportHasProblems(result.output, newProblemsSpec { HasConfigurationCacheProblemsSpec it ->
            it.checkReportProblems = true
            specAction.execute(it)
        })
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
        assertResultHasConsoleSummary(result, ConfigureUtil.configureUsing(specClosure))
    }

    void assertResultHasConsoleSummary(
        ExecutionResult result,
        Action<HasConfigurationCacheProblemsSpec> specAction = {}
    ) {
        assertHasConsoleSummary(result.output, newProblemsSpec(specAction))
    }

    private static void assertFailureDescription(
        ExecutionFailure failure,
        Matcher<String> failureMatcher
    ) {
        failure.assertThatDescription(failureMatcher)
    }

}
