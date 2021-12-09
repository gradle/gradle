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

package org.gradle.configurationcache.isolated

import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.tooling.provider.model.internal.QueryToolingModelBuildOperationType

class IsolatedProjectsFixture {
    private final AbstractIsolatedProjectsIntegrationTest spec
    private final BuildOperationsFixture buildOperations
    private final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations
    private final ConfigurationCacheFixture fixture

    IsolatedProjectsFixture(AbstractIsolatedProjectsIntegrationTest spec) {
        this.spec = spec
        this.buildOperations = new BuildOperationsFixture(spec.executer, spec.temporaryFolder)
        this.configurationCacheBuildOperations = new ConfigurationCacheBuildOperationsFixture(buildOperations)
        this.fixture = new ConfigurationCacheFixture(spec)
    }

    /**
     * Asserts that the cache entry is written with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStored(@DelegatesTo(StoreDetails) Closure closure) {
        def details = new StoreDetails()
        closure.delegate = details
        closure()

        assertHasStoreReason(details)
        spec.postBuildOutputContains("Configuration cache entry stored.")
        assertHasWarningThatIncubatingFeatureUsed()

        configurationCacheBuildOperations.assertStateStored()

        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry is written with some problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredWithProblems(@DelegatesTo(StoreWithProblemsDetails) Closure closure) {
        def details = new StoreWithProblemsDetails()
        closure.delegate = details
        closure()

        def totalProblems = details.totalProblems

        assertHasStoreReason(details)
        spec.result.assertHasPostBuildOutput("Configuration cache entry stored with ${details.problemsString}.")
        assertHasWarningThatIncubatingFeatureUsed()

        configurationCacheBuildOperations.assertStateStored()

        assertHasProblems(totalProblems, details.problems)

        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry is not written due to some problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredAndDiscarded(@DelegatesTo(StoreWithProblemsDetails) Closure closure) {
        def details = new StoreWithProblemsDetails()
        closure.delegate = details
        closure()

        fixture.assertStateStoredAndDiscarded(details, details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry is discarded and stored with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateRecreated(@DelegatesTo(StoreRecreatedDetails) Closure closure) {
        def details = new StoreRecreatedDetails()
        closure.delegate = details
        closure()

        doStateStored(details, "stored")
    }

    /**
     * Asserts that the cache entry is discarded and stored with the expected problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateRecreatedWithProblems(@DelegatesTo(StoreRecreatedWithProblemsDetails) Closure closure) {
        def details = new StoreRecreatedWithProblemsDetails()
        closure.delegate = details
        closure()

        doStoreWithProblems(details, details, "stored with $details.problemsString")
    }

    /**
     * Asserts that the cache entry is updated with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateUpdated(@DelegatesTo(StoreUpdateDetails) Closure closure) {
        def details = new StoreUpdateDetails()
        closure.delegate = details
        closure()

        doStateStored(details, "updated for ${details.updatedProjectsString}, ${details.reusedProjectsString} up-to-date")
    }

    /**
     * Asserts that the cache entry is updated with the given problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateUpdatedWithProblems(@DelegatesTo(StoreUpdatedWithProblemsDetails) Closure closure) {
        def details = new StoreUpdatedWithProblemsDetails()
        closure.delegate = details
        closure()

        doStoreWithProblems(details, details, "updated for ${details.updatedProjectsString} with $details.problemsString, ${details.reusedProjectsString} up-to-date")
    }

    private void doStateStored(StoreInvalidationDetails details, String storeAction) {
        assertHasRecreateReason(details)
        spec.postBuildOutputContains("Configuration cache entry $storeAction.")
        assertHasWarningThatIncubatingFeatureUsed()

        configurationCacheBuildOperations.assertStateStored()

        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    private void doStoreWithProblems(StoreInvalidationDetails details, ConfigurationCacheFixture.HasProblems problems, String storeAction) {
        def totalProblems = problems.totalProblems

        assertHasRecreateReason(details)
        spec.result.assertHasPostBuildOutput("Configuration cache entry $storeAction.")
        assertHasWarningThatIncubatingFeatureUsed()

        configurationCacheBuildOperations.assertStateStored()

        assertHasProblems(totalProblems, problems.problems)

        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    private void assertHasRecreateReason(StoreInvalidationDetails details) {
        // Inputs can be discovered in parallel, so required that any one of the changed inputs is reported
        def reasons = []
        details.changedFiles.each { file ->
            reasons.add("file '${file.replace('/', File.separator)}'")
        }
        if (details.changedGradleProperty) {
            reasons.add("the set of Gradle properties")
        }
        if (details.changedSystemProperty != null) {
            reasons.add("system property '$details.changedSystemProperty'")
        }
        if (details.changedTask != null) {
            reasons.add("an input to task '${details.changedTask}'")
        }

        def messages = reasons.collect { reason ->
            if (details.runsTasks) {
                "Creating task graph as configuration cache cannot be reused because $reason has changed."
            } else {
                "Creating tooling model as configuration cache cannot be reused because $reason has changed."
            }
        }

        def found = messages.any { message -> spec.output.contains(message) }
        assert found: "could not find expected invalidation reason in output. expected: ${messages}"
    }

    /**
     * Asserts that the cache entry is loaded and no projects are configured.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateLoaded(@DelegatesTo(LoadDetails) Closure closure = {}) {
        def details = new LoadDetails()
        closure.delegate = details
        closure()

        spec.outputContains("Reusing configuration cache.")
        spec.postBuildOutputContains("Configuration cache entry reused.")
        assertHasWarningThatIncubatingFeatureUsed()

        configurationCacheBuildOperations.assertStateLoaded()

        assertNothingConfigured()
        assertNoModelsQueried()
    }

    private void assertHasStoreReason(StoreDetails details) {
        if (details.runsTasks) {
            spec.outputContains("Calculating task graph as no configuration cache is available for tasks:")
        } else {
            spec.outputContains("Creating tooling model as no configuration cache is available for the requested model")
        }
    }


    private assertHasProblems(int totalProblems, List<ConfigurationCacheFixture.ProblemDetails> problems) {
        spec.problems.assertResultHasProblems(spec.result) {
            withTotalProblemsCount(totalProblems)
            withUniqueProblems(problems.collect {
                it.message.replace('/', File.separator)
            })
        }
    }

    private void assertNothingConfigured() {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        // A synthetic "project configured" operation is fired for each root project for build scans
        assert configuredProjects.every { it.details.projectPath == ':' }

        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
        assert scripts.empty
    }

    private void assertProjectsConfigured(StoreDetails details) {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        assert configuredProjects.collect { fullPath(it) }.toSet() == details.projects

        // Scripts - one or more for settings, and one for each project build script
        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
        assert !scripts.empty
        assert scripts.first().details.targetType == "settings"
        def otherScripts = scripts.findAll { it.details.targetType != "settings" }
        assert otherScripts.size() == projectsWithScripts(details.projects).size()
    }

    private void assertNoModelsQueried() {
        def models = buildOperations.all(QueryToolingModelBuildOperationType)
        assert models.empty
    }

    private void assertModelsQueried(StoreDetails details) {
        def models = buildOperations.all(QueryToolingModelBuildOperationType)
        def expectedProjectModels = details.models.collect { [it.path] * it.count }.flatten()
        assert models.size() == expectedProjectModels.size() + details.buildModelQueries
        models.removeAll { it.details.projectPath == null }
        def sortedProjectModels = models.collect { fullPath(it) }.sort()
        def sortedExpectedProjectModels = expectedProjectModels.sort()
        assert sortedProjectModels == sortedExpectedProjectModels
    }

    private void assertHasWarningThatIncubatingFeatureUsed() {
        spec.outputContains(ConfigurationCacheFixture.ISOLATED_PROJECTS_MESSAGE)
        spec.outputDoesNotContain(ConfigurationCacheFixture.CONFIGURATION_CACHE_MESSAGE)
        spec.outputDoesNotContain(ConfigurationCacheFixture.CONFIGURE_ON_DEMAND_MESSAGE)
    }

    private String fullPath(BuildOperationRecord operationRecord) {
        if (operationRecord.details.buildPath == ':') {
            return operationRecord.details.projectPath
        } else if (operationRecord.details.projectPath == ':') {
            return operationRecord.details.buildPath
        } else {
            return operationRecord.details.buildPath + operationRecord.details.projectPath
        }
    }

    private List<String> projectsWithScripts(Collection<String> projects) {
        def result = []
        for (path in projects) {
            def baseName = path == ':' ? "build" : (path.drop(1).replace(':', '/') + "/build")
            if (spec.file("${baseName}.gradle").isFile() || spec.file("${baseName}.gradle.kts").isFile()) {
                result.add(path)
            }
        }
        return result
    }

    static class StoreDetails implements ConfigurationCacheFixture.HasBuildActions {
        final projects = new HashSet<String>()
        final List<ModelDetails> models = []
        int buildModelQueries

        void projectConfigured(String path) {
            projects.add(path)
        }

        void projectsConfigured(String... paths) {
            projects.addAll(paths.toList())
        }

        /**
         * The given number of build scoped models are created.
         */
        void buildModelCreated(int count = 1) {
            runsTasks = false
            buildModelQueries += count
        }

        /**
         * One model is created for each of the given projects. The projects will also be configured
         */
        void modelsCreated(String... paths) {
            projectsConfigured(paths)
            runsTasks = false
            models.addAll(paths.collect { new ModelDetails(it, 1) })
        }

        /**
         * The given number of models are created for the given project. The project will also be configured
         */
        void modelsCreated(String path, int count) {
            projectsConfigured(path)
            runsTasks = false
            models.add(new ModelDetails(path, count))
        }

        void modelsQueriedAndNotPresent(String... paths) {
            for (path in paths) {
                modelsCreated(path, 0)
            }
        }
    }

    static class StoreWithProblemsDetails extends StoreDetails implements ConfigurationCacheFixture.HasProblems {
    }

    static class StoreInvalidationDetails extends StoreDetails {
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

    static class StoreRecreatedDetails extends StoreInvalidationDetails {
    }

    static class StoreRecreatedWithProblemsDetails extends StoreRecreatedDetails implements ConfigurationCacheFixture.HasProblems {
    }

    static class StoreUpdateDetails extends StoreInvalidationDetails {
        Set<String> projectsReused = new HashSet<>()

        void modelsReused(String... paths) {
            projectsReused.addAll(paths.toList())
        }

        String getUpdatedProjectsString() {
            def updatedProjects = models.size()
            return updatedProjects == 1 ? "1 project" : "$updatedProjects projects"
        }

        String getReusedProjectsString() {
            def reusedProjects = projectsReused.size()
            switch (reusedProjects) {
                case 0:
                    return "no projects"
                case 1:
                    return "1 project"
                default:
                    return "${reusedProjects} projects"
            }
        }
    }

    static class StoreUpdatedWithProblemsDetails extends StoreUpdateDetails implements ConfigurationCacheFixture.HasProblems {
    }

    static class LoadDetails {
    }

    static class ModelDetails {
        final String path
        final int count

        ModelDetails(String path, int count) {
            this.path = path
            this.count = count
        }
    }
}
