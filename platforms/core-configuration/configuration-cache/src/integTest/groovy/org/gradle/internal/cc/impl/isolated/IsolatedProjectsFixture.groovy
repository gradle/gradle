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

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import org.gradle.configuration.ApplyScriptPluginBuildOperationType
import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.internal.cc.impl.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasBuildActions
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasInvalidationReason
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.tooling.provider.model.internal.QueryToolingModelBuildOperationType

class IsolatedProjectsFixture {
    private final AbstractConfigurationCacheOptInFeatureIntegrationTest spec
    private final ConfigurationCacheFixture configurationCache
    private final BuildOperationsFixture buildOperations
    private final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations

    IsolatedProjectsFixture(AbstractConfigurationCacheOptInFeatureIntegrationTest spec) {
        this.spec = spec
        this.configurationCache = new ConfigurationCacheFixture(spec)
        this.buildOperations = configurationCache.buildOperations
        this.configurationCacheBuildOperations = configurationCache.configurationCacheBuildOperations
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

        configurationCache.assertStateStored(details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(details)
        assertModelsQueried(details)
    }

    /**
     * Asserts that the cache entry was written with no problems.
     *
     * Also asserts that the expected set of projects is configured, the expected models are queried
     * and the appropriate console logging, reports and build operations are generated.
     */
    void assertModelStored(@DelegatesTo(StoreDetails) Closure closure) {
        def details = forModels(new StoreDetails())
        closure.delegate = details
        closure()

        configurationCache.assertStateStored(details)

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
    void assertModelStoredWithProblems(@DelegatesTo(StateStoreWithProblemsDetails) Closure closure) {
        def details = forModels(new StateStoreWithProblemsDetails())
        closure.delegate = details
        closure()

        configurationCache.assertStateStoredWithProblems(details, details)

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

        configurationCache.assertStateStoredAndDiscarded(details, details)

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
    void assertModelStoredAndDiscarded(@DelegatesTo(StateDiscardedWithProblemsDetails) Closure closure) {
        def details = forModels(new StateDiscardedWithProblemsDetails())
        closure.delegate = details
        closure()

        configurationCache.assertStateStoredAndDiscarded(details, details)

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
    void assertModelRecreated(@DelegatesTo(StoreRecreateDetails) Closure closure) {
        def details = forModels(new StoreRecreateDetails())
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
    void assertModelUpdated(@DelegatesTo(StoreUpdateDetails) Closure closure) {
        def details = forModels(new StoreUpdateDetails())
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
    void assertModelUpdatedWithProblems(@DelegatesTo(StoreUpdatedWithProblemsDetails) Closure closure) {
        def details = forModels(new StoreUpdatedWithProblemsDetails())
        closure.delegate = details
        closure()

        doStoreWithProblems(details, details, details, details)
    }

    private void doStateStored(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails) {
        configurationCache.assertStateRecreated(details, invalidationDetails)

        assertHasWarningThatIncubatingFeatureUsed()
        assertProjectsConfigured(intermediateDetails)
        assertModelsQueried(intermediateDetails)
    }

    private void doStoreWithProblems(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails, ConfigurationCacheFixture.HasProblems problems) {
        configurationCache.assertStateRecreatedWithProblems(details, invalidationDetails, problems)

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
        configurationCache.assertStateLoaded(new ConfigurationCacheFixture.LoadDetails())

        assertHasWarningThatIncubatingFeatureUsed()
        assertNoModelsQueried()
    }

    /**
     * Asserts that the cache entry was loaded and no projects are configured.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertModelLoaded(@DelegatesTo(ConfigurationCacheFixture.LoadDetails) Closure closure = {}) {
        def details = forModels(new ConfigurationCacheFixture.LoadDetails())
        closure.delegate = details
        closure()

        configurationCache.assertStateLoaded(details)

        assertHasWarningThatIncubatingFeatureUsed()
        assertNoModelsQueried()
    }

    void assertNoConfigurationCache() {
        configurationCacheBuildOperations.assertNoConfigurationCache()
    }

    private void assertProjectsConfigured(HasIntermediateDetails details) {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        assert configuredProjects.collect { fullPath(it) }.toSet() == details.projects

        // Scripts - one or more for settings, and one for each project build script
        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
        assert !scripts.empty
        def sortedScripts = scripts.toSorted { it -> it.startTime }
        assert sortedScripts.first().details.targetType == "settings"
        def nonSettingsScripts = scripts.findAll { it.details.targetType != "settings" }
        def nonSettingsScriptTargets = nonSettingsScripts.collect { fullPath(it.details.buildPath, it.details.targetPath) }.toSet()
        assert nonSettingsScriptTargets.size() == projectsWithBuildScripts(details.projects).size()
    }

    private void assertNoModelsQueried() {
        def models = modelRequests()
        assert models.empty
    }

    private void assertModelsQueried(HasIntermediateDetails details) {
        def models = modelRequests().toSorted { it.buildTreePath }
        def (buildScopedModels, projectScopedModels) = models.split { it.buildScoped }
        assert buildScopedModels.size() == details.buildModelQueries

        // Count of 0 is a special case used by `modelsQueriedAndNotPresent`
        def modelExpectations = details.models.findAll { it.count > 0 }
        def expectedProjectModels = modelExpectations.groupBy { it.path }
        def expectedProjectModelsCounts = remapValues(expectedProjectModels) { it.count.sum() as int }

        def projectModels = projectScopedModels.groupBy { it.buildTreePath }
        def projectModelsShortNames = remapValues(projectModels) { it.shortModelName }

        // Do not extract left side into a variable to get power assert insights
        assert remapValues(projectModelsShortNames) { it.size() } == expectedProjectModelsCounts

        for (def modelExpectation in modelExpectations) {
            def buildTreePath = modelExpectation.path
            if (modelExpectation.modelNames != null) {
                def projectModelNames =  projectModels[buildTreePath].modelName.toSorted()
                assert projectModelNames == modelExpectation.modelNames.toSorted()
            }
        }
    }

    private void assertHasWarningThatIncubatingFeatureUsed() {
        spec.outputContains(ConfigurationCacheFixture.ISOLATED_PROJECTS_MESSAGE)
        spec.outputDoesNotContain(ConfigurationCacheFixture.CONFIGURE_ON_DEMAND_MESSAGE)
    }

    private List<ModelRequest> modelRequests() {
        buildOperations.all(QueryToolingModelBuildOperationType).collect { new ModelRequest(it) }
    }

    private static String fullPath(BuildOperationRecord operationRecord) {
        return fullPath(operationRecord.details.buildPath, operationRecord.details.projectPath)
    }

    private static String fullPath(String buildPath, String projectPath) {
        if (buildPath == ':') {
            return projectPath
        } else if (projectPath == ':') {
            return buildPath
        } else {
            return buildPath + projectPath
        }
    }

    private List<String> projectsWithBuildScripts(Collection<String> projects) {
        def result = []
        for (path in projects) {
            def baseName = path == ':' ? "build" : (path.drop(1).replace(':', '/') + "/build")
            if (spec.file("${baseName}.gradle").isFile() || spec.file("${baseName}.gradle.kts").isFile()) {
                result.add(path)
            }
        }
        return result
    }

    private <T extends HasBuildActions> T forModels(T details) {
        details.createsModels = true
        details.runsTasks = false
        details.loadsOnStore = false
        details
    }

    trait HasIntermediateDetails extends HasBuildActions {
        final projects = new HashSet<String>()
        final List<ModelRequestExpectation> models = []
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
            buildModelQueries += count
        }

        /**
         * One model is created for each of the given projects. The projects will also be configured
         */
        void modelsCreated(String... paths) {
            projectsConfigured(paths)
            models.addAll(paths.collect { new ModelRequestExpectation(it, 1) })
        }

        /**
         * One model is created for each of the given projects. The projects will also be configured
         */
        void modelsCreated(Class<?> modelType, String... paths) {
            projectsConfigured(paths)
            models.addAll(paths.collect { new ModelRequestExpectation(it, [modelType.name]) })
        }

        /**
         * The given number of models are created for the given project. The project will also be configured
         */
        void modelsCreated(String path, int count) {
            modelsCreated(new ModelRequestExpectation(path, count))
        }

        /**
         * The models are created for the given project. The project will also be configured
         */
        void modelsCreated(String path, Class<?> modelType, Class<?>... moreModelTypes) {
            modelsCreated(path, [modelType, *moreModelTypes].name)
        }

        /**
         * The models are created for the given projects. The projects will also be configured
         */
        void modelsCreated(List<String> paths, List<String> modelNames) {
            for (def path in paths) {
                modelsCreated(new ModelRequestExpectation(path, modelNames))
            }
        }

        /**
         * The models are created for the given project. The project will also be configured
         */
        void modelsCreated(String path, List<String> modelNames) {
            modelsCreated(new ModelRequestExpectation(path, modelNames))
        }

        void modelsCreated(ModelRequestExpectation expectation) {
            projectsConfigured(expectation.path)
            models.add(expectation)
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
            return getPluralProjectsString(updatedProjects)
        }

        String getReusedProjectsString() {
            def reusedProjects = projectsReused.size()
            return getPluralProjectsString(reusedProjects)
        }

        private static String getPluralProjectsString(int amount) {
            switch (amount) {
                case 0:
                    return "no projects"
                case 1:
                    return "1 project"
                default:
                    return "${amount} projects"
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

    static class ModelRequestExpectation {
        final String path
        final int count
        final List<String> modelNames

        ModelRequestExpectation(String path, int count) {
            this.path = path
            this.count = count
            this.modelNames = null
        }

        ModelRequestExpectation(String path, List<String> names) {
            this.path = path
            this.count = names.size()
            this.modelNames = names
        }

        @Override
        String toString() {
            "Expectation for project '$path': ${modelNames ?: count} models"
        }
    }

    static class ModelRequest {
        final BuildOperationRecord operationRecord
        final String modelName

        ModelRequest(BuildOperationRecord operationRecord) {
            this.operationRecord = operationRecord
            this.modelName = (operationRecord.displayName =~ /Build model '(.+?)'/)[0][1]
        }

        String getBuildTreePath() {
            fullPath(operationRecord)
        }

        boolean isBuildScoped() {
            operationRecord.details.projectPath == null
        }

        String getShortModelName() {
            abbreviateClassName(modelName)
        }

        @Override
        String toString() {
            String.format("Model '%s' for %s '%s'", shortModelName, buildScoped ? 'build' : 'project', buildTreePath)
        }
    }

    static <K, V, R> Map<K, R> remapValues(Map<K, V> self, @ClosureParams(FirstParam.SecondGenericType.class) Closure<R> transform) {
        self.collectEntries { K key, V value -> [(key): transform(value)] }
    }

    // `org.gradle.tooling.model.gradle.GradleBuild` -> `o.g.t.m.g.GradleBuild`
    static abbreviateClassName(String fullClassName) {
        fullClassName.replaceAll(/(\b\w)\w+\./) { "${it[1]}." }
    }
}
