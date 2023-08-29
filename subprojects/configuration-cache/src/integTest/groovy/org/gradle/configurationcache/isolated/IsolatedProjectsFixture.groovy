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
import org.gradle.configurationcache.fixtures.AbstractOptInFeatureIntegrationTest
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasBuildActions
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasInvalidationReason
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.tooling.provider.model.internal.QueryToolingModelBuildOperationType

class IsolatedProjectsFixture {
    private final AbstractOptInFeatureIntegrationTest spec
    private final ConfigurationCacheFixture fixture
    private final BuildOperationsFixture buildOperations
    private final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations

    IsolatedProjectsFixture(AbstractOptInFeatureIntegrationTest spec) {
        this.spec = spec
        this.fixture = new ConfigurationCacheFixture(spec)
        this.buildOperations = fixture.buildOperations
        this.configurationCacheBuildOperations = fixture.configurationCacheBuildOperations
    }

    /**
     * Asserts that the cache entry was written with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStored(@DelegatesTo(StoreDetails) Closure closure) {
        def details = new StoreDetails()
        closure.delegate = details
        closure()

        fixture.assertStateStored(details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry was written with some problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredWithProblems(@DelegatesTo(StateStoreWithProblemsDetails) Closure closure) {
        def details = new StateStoreWithProblemsDetails()
        closure.delegate = details
        closure()

        fixture.assertStateStoredWithProblems(details, details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry was written but discarded due to some problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateStoredAndDiscarded(@DelegatesTo(StateDiscardedWithProblemsDetails) Closure closure) {
        def details = new StateDiscardedWithProblemsDetails()
        closure.delegate = details
        closure()

        fixture.assertStateStoredAndDiscarded(details, details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry was discarded and stored again with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateRecreated(@DelegatesTo(StoreRecreateDetails) Closure closure) {
        def details = new StoreRecreateDetails()
        closure.delegate = details
        closure()

        doStateStored(details, details, details)
    }

    /**
     * Asserts that the cache entry was updated with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateUpdated(@DelegatesTo(StoreUpdateDetails) Closure closure) {
        def details = new StoreUpdateDetails()
        closure.delegate = details
        closure()

        doStateStored(details, details, details)
    }

    /**
     * Asserts that the cache entry was updated with the given problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertStateUpdatedWithProblems(@DelegatesTo(StoreUpdatedWithProblemsDetails) Closure closure) {
        def details = new StoreUpdatedWithProblemsDetails()
        closure.delegate = details
        closure()

        doStoreWithProblems(details, details, details, details)
    }

    private void doStateStored(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails) {
        fixture.assertStateRecreated(details, invalidationDetails)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(intermediateDetails)
        assertModelsQueried(intermediateDetails)
    }

    private void doStoreWithProblems(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails, ConfigurationCacheFixture.HasProblems problems) {
        fixture.assertStateRecreatedWithProblems(details, invalidationDetails, problems)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(intermediateDetails)
        assertModelsQueried(intermediateDetails)
    }

    /**
     * Asserts that the cache entry was loaded and no projects are configured.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateLoaded() {
        fixture.assertStateLoaded(new ConfigurationCacheFixture.LoadDetails())

        assertHasWarningThatIncubatingFeatureUsed()
        assertNoModelsQueried()
    }

    private void assertProjectsConfigured(HasIntermediateDetails details) {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        assert configuredProjects.collect { fullPath(it) }.toSet() == details.projects

        // Scripts - one or more for settings, and one for each project build script
        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
        assert !scripts.empty
        def sortedScripts = scripts.toSorted { it -> it.startTime }
        assert sortedScripts.first().details.targetType == "settings"
        def otherScripts = scripts.findAll { it.details.targetType != "settings" }
        assert otherScripts.size() == projectsWithScripts(details.projects).size()
    }

    private void assertNoModelsQueried() {
        def models = buildOperations.all(QueryToolingModelBuildOperationType)
        assert models.empty
    }

    private void assertModelsQueried(HasIntermediateDetails details) {
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

    trait HasIntermediateDetails {
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
            loadsOnStore = false
            buildModelQueries += count
        }

        /**
         * One model is created for each of the given projects. The projects will also be configured
         */
        void modelsCreated(String... paths) {
            projectsConfigured(paths)
            runsTasks = false
            loadsOnStore = false
            models.addAll(paths.collect { new ModelDetails(it, 1) })
        }

        /**
         * The given number of models are created for the given project. The project will also be configured
         */
        void modelsCreated(String path, int count) {
            projectsConfigured(path)
            runsTasks = false
            loadsOnStore = false
            models.add(new ModelDetails(path, count))
        }

        void modelsQueriedAndNotPresent(String... paths) {
            for (path in paths) {
                modelsCreated(path, 0)
            }
        }
    }

    static class StoreDetails extends ConfigurationCacheFixture.StateStoreDetails implements HasIntermediateDetails {
    }

    static class StateStoreWithProblemsDetails extends ConfigurationCacheFixture.StateStoreWithProblemsDetails implements HasIntermediateDetails {
    }

    static class StateDiscardedWithProblemsDetails extends ConfigurationCacheFixture.StateDiscardedWithProblemsDetails implements HasIntermediateDetails {
    }

    static class StoreRecreateDetails extends ConfigurationCacheFixture.StateRecreateDetails implements HasIntermediateDetails {
    }

    static class StoreUpdateDetails extends ConfigurationCacheFixture.StateRecreateDetails implements HasIntermediateDetails {
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

        @Override
        String getStoreAction() {
            return "updated for ${updatedProjectsString}, ${reusedProjectsString} up-to-date"
        }
    }

    static class StoreUpdatedWithProblemsDetails extends StoreUpdateDetails implements ConfigurationCacheFixture.HasProblems {
        @Override
        String getStoreAction() {
            return "updated for ${updatedProjectsString} with $problemsString, ${reusedProjectsString} up-to-date"
        }
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
