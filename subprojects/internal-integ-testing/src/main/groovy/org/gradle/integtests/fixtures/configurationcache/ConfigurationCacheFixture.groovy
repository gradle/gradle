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

package org.gradle.integtests.fixtures.configurationcache

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure

class ConfigurationCacheFixture {
    static final String CONFIGURATION_CACHE_MESSAGE = "Configuration cache is an incubating feature."
    static final String ISOLATED_PROJECTS_MESSAGE = "Isolated projects is an incubating feature."
    static final String CONFIGURE_ON_DEMAND_MESSAGE = "Configuration on demand is an incubating feature."

    private final AbstractIntegrationSpec spec
    private final BuildOperationsFixture buildOperations
    private final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations
    private final ConfigurationCacheProblemsFixture problems

    ConfigurationCacheFixture(AbstractIntegrationSpec spec) {
        this.spec = spec
        buildOperations = new BuildOperationsFixture(spec.executer, spec.temporaryFolder)
        configurationCacheBuildOperations = new ConfigurationCacheBuildOperationsFixture(buildOperations)
        problems = new ConfigurationCacheProblemsFixture(spec.executer, spec.testDirectory)
    }

    /**
     * Asserts that the cache entry is stored but discarded with the given problems.
     */
    void assertStateStoredAndDiscarded(@DelegatesTo(StoreWithProblemsDetails) Closure closure) {
        def details = new StoreWithProblemsDetails()
        closure.delegate = details
        closure()

        assertStateStoredAndDiscarded(details, details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    /**
     * Asserts that the cache entry is stored but discarded with the given problems.
     */
    void assertStateStoredAndDiscarded(HasBuildActions details, HasProblems problemDetails) {

        assertHasStoreReason(details)
        configurationCacheBuildOperations.assertStateStored()

        boolean isFailure = spec.result instanceof ExecutionFailure

        def totalProblems = problemDetails.totalProblems
        def message
        if (totalProblems == 1) {
            message = "Configuration cache entry discarded with 1 problem."
        } else {
            message = "Configuration cache entry discarded with ${totalProblems} problems."
        }
        if (isFailure) {
            spec.outputContains(message)
        } else {
            spec.postBuildOutputContains(message)
        }

        if (isFailure) {
            problems.assertFailureHasProblems(spec.failure) {
                applyProblemsTo(problemDetails, delegate)
            }
        } else {
            problems.assertResultHasProblems(spec.result) {
                applyProblemsTo(problemDetails, delegate)
            }
        }
    }

    private void applyProblemsTo(HasProblems details, HasConfigurationCacheProblemsSpec spec) {
        spec.withTotalProblemsCount(details.totalProblems)
        spec.problemsWithStackTraceCount = details.problemsWithStackTrace
        spec.withUniqueProblems(details.problems.collect {
            it.message.replace('/', File.separator)
        })
    }

    private void assertHasWarningThatIncubatingFeatureUsed() {
        spec.outputContains(CONFIGURATION_CACHE_MESSAGE)
        spec.outputDoesNotContain(ISOLATED_PROJECTS_MESSAGE)
        spec.outputDoesNotContain(CONFIGURE_ON_DEMAND_MESSAGE)
    }

    private void assertHasStoreReason(HasBuildActions details) {
        if (details.runsTasks) {
            spec.outputContains("Calculating task graph as no configuration cache is available for tasks:")
        } else {
            spec.outputContains("Creating tooling model as no configuration cache is available for the requested model")
        }
    }

    static class ProblemDetails {
        final String message
        final int count
        final boolean hasStackTrace

        ProblemDetails(String message, int count, boolean hasStackTrace) {
            this.message = message
            this.count = count
            this.hasStackTrace = hasStackTrace
        }
    }

    trait HasProblems {
        final List<ProblemDetails> problems = []

        void problem(String message, int count = 1, boolean hasStackTrace = true) {
            problems.add(new ProblemDetails(message, count, hasStackTrace))
        }

        void serializationProblem(String message, int count = 1) {
            problems.add(new ProblemDetails(message, count, false))
        }

        int getTotalProblems() {
            return problems.inject(0) { a, b -> a + b.count }
        }

        int getProblemsWithStackTrace() {
            return problems.inject(0) { a, b -> a + (b.hasStackTrace ? b.count : 0) }
        }

        String getProblemsString() {
            def count = totalProblems
            return count == 1 ? "1 problem" : "$count problems"
        }
    }

    trait HasBuildActions {
        boolean runsTasks = true
    }

    static class StoreDetails implements HasBuildActions {
    }

    static class StoreWithProblemsDetails extends StoreDetails implements HasProblems {
    }
}
