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

import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class ConfigurationCacheFixture {
    static final String ISOLATED_PROJECTS_MESSAGE = "Isolated projects is an incubating feature."
    static final String CONFIGURE_ON_DEMAND_MESSAGE = "Configuration on demand is an incubating feature."

    private final AbstractIntegrationSpec spec
    final BuildOperationsFixture buildOperations
    final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations
    final ConfigurationCacheProblemsFixture problems

    ConfigurationCacheFixture(AbstractIntegrationSpec spec) {
        this.spec = spec
        buildOperations = new BuildOperationsFixture(spec.executer, spec.temporaryFolder)
        configurationCacheBuildOperations = new ConfigurationCacheBuildOperationsFixture(buildOperations)
        problems = new ConfigurationCacheProblemsFixture(spec.executer, spec.testDirectory)
    }

    /**
     * Asserts that the configuration cache was not enabled.
     */
    void assertNotEnabled() {
        spec.outputDoesNotContain(ISOLATED_PROJECTS_MESSAGE)
        spec.outputDoesNotContain(CONFIGURE_ON_DEMAND_MESSAGE)
        configurationCacheBuildOperations.assertNoConfigurationCache()
        assertHasNoProblems()
    }

    /**
     * Asserts that the cache entry was written with no problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStored(@DelegatesTo(StateStoreDetails) Closure closure = {}) {
        def details = new StateStoreDetails()
        closure.delegate = details
        closure()

        assertStateStored(details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateStored(HasBuildActions details) {
        assertHasStoreReason(details)
        configurationCacheBuildOperations.assertStateStored(details.loadsOnStore)

        spec.postBuildOutputContains("Configuration cache entry ${details.storeAction}.")

        assertHasNoProblems()
    }

    /**
     * Asserts that the cache entry was stored with the given problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredWithProblems(@DelegatesTo(StateStoreWithProblemsDetails) Closure closure) {
        def details = new StateStoreWithProblemsDetails()
        closure.delegate = details
        closure()

        assertStateStoredWithProblems(details, details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateStoredWithProblems(HasBuildActions details, HasProblems problemDetails) {
        assertHasStoreReason(details)
        configurationCacheBuildOperations.assertStateStored(details.runsTasks)

        spec.result.assertHasPostBuildOutput("Configuration cache entry ${details.storeAction}.")

        assertHasProblems(problemDetails)
    }

    /**
     * Asserts that the cache entry was stored but discarded with the given problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredAndDiscarded(@DelegatesTo(StateDiscardedWithProblemsDetails) Closure closure) {
        def details = new StateDiscardedWithProblemsDetails()
        closure.delegate = details
        closure()

        assertStateStoredAndDiscarded(details, details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateStoredAndDiscarded(HasBuildActions details, HasProblems problemDetails) {
        assertHasStoreReason(details)
        configurationCacheBuildOperations.assertStateStored(false)

        def message = "Configuration cache entry ${details.storeAction}"
        boolean isFailure = spec.result instanceof ExecutionFailure
        if (isFailure) {
            spec.outputContains(message)
        } else {
            spec.postBuildOutputContains(message)
        }

        assertHasProblems(problemDetails)
    }

    /**
     * Asserts that the cache entry was discarded due to some input change and stored again with no problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateRecreated(@DelegatesTo(StateRecreateDetails) Closure closure) {
        def details = new StateRecreateDetails()
        closure.delegate = details
        closure()

        assertStateRecreated(details, details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateRecreated(HasBuildActions details, HasInvalidationReason invalidationDetails) {
        assertHasRecreateReason(details, invalidationDetails)
        configurationCacheBuildOperations.assertStateStored(details.runsTasks)
        spec.postBuildOutputContains("Configuration cache entry ${details.storeAction}.")
        assertHasNoProblems()
    }

    /**
     * Asserts that the cache entry was discarded due to some input change and stored again with the given problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateRecreatedWithProblems(@DelegatesTo(StateRecreateWithProblemsDetails) Closure closure) {
        def details = new StateRecreateWithProblemsDetails()
        closure.delegate = details
        closure()

        assertStateRecreatedWithProblems(details, details, details)
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateRecreatedWithProblems(HasBuildActions details, HasInvalidationReason invalidationDetails, HasProblems problemDetails) {
        assertHasRecreateReason(details, invalidationDetails)
        configurationCacheBuildOperations.assertStateStored(false)
        spec.postBuildOutputContains("Configuration cache entry ${details.storeAction}.")
        assertHasProblems(problemDetails)
    }

    /**
     * Asserts that the cache entry was loaded and used with no problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateLoaded() {
        assertStateLoaded(new LoadDetails())
        assertHasWarningThatIncubatingFeatureUsed()
    }

    void assertStateLoaded(LoadDetails details) {
        assertLoadLogged()
        spec.postBuildOutputContains("Configuration cache entry ${details.storeAction}.")

        configurationCacheBuildOperations.assertStateLoaded()

        assertNothingConfigured()

        assertHasNoProblems()
    }

    /**
     * Asserts that the cache entry was loaded and used with the given problems.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateLoadedWithProblems(@DelegatesTo(LoadWithProblemsDetails) Closure closure) {
        def details = new LoadWithProblemsDetails()
        closure.delegate = details
        closure()

        assertHasWarningThatIncubatingFeatureUsed()
        assertLoadLogged()
        spec.postBuildOutputContains("Configuration cache entry ${details.storeAction}.")

        configurationCacheBuildOperations.assertStateLoaded()

        assertNothingConfigured()

        assertHasProblems(details)
    }

    private void assertHasProblems(HasProblems problemDetails) {
        if (spec.failed) {
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

    private assertHasNoProblems() {
        problems.assertResultHasProblems(spec.result) {
        }
    }

    private void assertHasWarningThatIncubatingFeatureUsed() {
        if (quietLogging) {
            // Runs in quiet mode, and does not log anything
            return
        }
        spec.outputDoesNotContain(ISOLATED_PROJECTS_MESSAGE)
        spec.outputDoesNotContain(CONFIGURE_ON_DEMAND_MESSAGE)
    }

    private assertLoadLogged() {
        if (quietLogging) {
            // Runs in quiet mode, and does not log anything
            return
        }
        spec.outputContains("Reusing configuration cache.")
    }

    private void assertHasStoreReason(HasBuildActions details) {
        if (quietLogging) {
            // Runs in quiet mode, and does not log anything
            return
        }
        if (details.runsTasks) {
            spec.outputContains("Calculating task graph as no cached configuration is available for tasks:")
        } else {
            spec.outputContains("Creating tooling model as no cached configuration is available for the requested model")
        }
    }

    private boolean isQuietLogging() {
        spec.executer instanceof GradleContextualExecuter && GradleContextualExecuter.configCache
    }

    private void assertHasRecreateReason(HasBuildActions details, HasInvalidationReason invalidationDetails) {
        // Inputs can be discovered in parallel, so require that any one of the changed inputs is reported

        def reasons = []
        invalidationDetails.changedFiles.each { file ->
            reasons.add("file '${file.replace('/', File.separator)}'")
        }
        if (invalidationDetails.changedGradleProperty) {
            reasons.add("the set of Gradle properties")
        }
        if (invalidationDetails.changedSystemProperty != null) {
            reasons.add("system property '$invalidationDetails.changedSystemProperty'")
        }
        if (invalidationDetails.changedTask != null) {
            reasons.add("an input to task '${invalidationDetails.changedTask}'")
        }

        def messages = reasons.collect { reason ->
            if (details.runsTasks) {
                "Calculating task graph as configuration cache cannot be reused because $reason has changed."
            } else {
                "Creating tooling model as configuration cache cannot be reused because $reason has changed."
            }
        }

        def found = messages.any { message -> spec.output.contains(message) }
        assert found: "could not find expected invalidation reason in output. expected: ${messages}"
    }

    private void assertNothingConfigured() {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        // A synthetic "project configured" operation is fired for each root project for build scans
        assert configuredProjects.every { it.details.projectPath == ':' }

        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
        assert scripts.empty
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
        boolean loadsOnStore = true

        abstract String getStoreAction()
    }

    trait HasInvalidationReason {
        List<String> changedFiles = []
        boolean changedGradleProperty
        String changedSystemProperty
        String changedTask

        void fileChanged(String name) {
            changedFiles.add(name)
        }

        void taskInputChanged(String name) {
            changedTask = name
        }

        void gradlePropertyChanged() {
            changedGradleProperty = true
        }

        void systemPropertyChanged(String name) {
            changedSystemProperty = name
        }
    }

    static class StateStoreDetails implements HasBuildActions {
        @Override
        String getStoreAction() {
            return "stored"
        }
    }

    static class StateStoreWithProblemsDetails implements HasBuildActions, HasProblems {
        @Override
        String getStoreAction() {
            return "stored with ${problemsString}"
        }
    }

    static class StateDiscardedWithProblemsDetails implements HasBuildActions, HasProblems {
        @Override
        String getStoreAction() {
            if (totalProblems == 0) {
                return "discarded"
            } else {
                return "discarded with ${problemsString}"
            }
        }
    }

    static class StateRecreateDetails extends StateStoreDetails implements HasInvalidationReason {
    }

    static class StateRecreateWithProblemsDetails extends StateStoreWithProblemsDetails implements HasInvalidationReason {
    }

    static class LoadDetails implements HasBuildActions {
        @Override
        String getStoreAction() {
            return "reused"
        }
    }

    static class LoadWithProblemsDetails implements HasBuildActions, HasProblems {
        @Override
        String getStoreAction() {
            return "reused with ${problemsString}"
        }
    }
}
