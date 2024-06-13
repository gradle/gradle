/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.impl.tapi


import org.gradle.configuration.project.ConfigureProjectBuildOperationType
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheBuildOperationsFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasBuildActions
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture.HasInvalidationReason
import org.gradle.internal.cc.impl.fixtures.AbstractConfigurationCacheOptInFeatureIntegrationTest
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.tooling.provider.model.internal.QueryToolingModelBuildOperationType

class ConfigurationCacheToolingApiFixture {
    private final AbstractConfigurationCacheOptInFeatureIntegrationTest spec
    private final ConfigurationCacheFixture fixture
    private final BuildOperationsFixture buildOperations
    private final ConfigurationCacheBuildOperationsFixture configurationCacheBuildOperations

    ConfigurationCacheToolingApiFixture(AbstractConfigurationCacheOptInFeatureIntegrationTest spec) {
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

        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateStored(details)

        assertProjectsConfigured(details.projectConfigured)
        assertModelsQueried()
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

        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateStoredWithProblems(details, details)

        assertProjectsConfigured(details.projectConfigured)
        assertModelsQueried()
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

        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateStoredAndDiscarded(details, details)

        assertProjectsConfigured(details.projectConfigured)
        assertModelsQueried()
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

    private void doStateStored(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails) {
        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateRecreated(details, invalidationDetails)

        assertProjectsConfigured(intermediateDetails.projectConfigured)
        assertModelsQueried()
    }

    private void doStoreWithProblems(HasBuildActions details, HasInvalidationReason invalidationDetails, HasIntermediateDetails intermediateDetails, ConfigurationCacheFixture.HasProblems problems) {
        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateRecreatedWithProblems(details, invalidationDetails, problems)

        assertProjectsConfigured(intermediateDetails.projectConfigured)
        assertModelsQueried()
    }

    /**
     * Asserts that the cache entry was loaded and no projects are configured.
     *
     * Also asserts that the appropriate console logging, reports and build operations are generated.
     */
    void assertStateLoaded() {
        fixture.assertNoWarningThatIncubatingFeatureUsed()
        fixture.assertStateLoaded(new ConfigurationCacheFixture.LoadDetails())

        assertProjectsConfigured(0)
        assertNoModelsQueried()
    }

    void assertNoConfigurationCache() {
        configurationCacheBuildOperations.assertNoConfigurationCache()
    }

    private void assertProjectsConfigured(int projectsConfigured) {
        def configuredProjects = buildOperations.all(ConfigureProjectBuildOperationType)
        assert configuredProjects.collect { fullPath(it) }.toSet().size() == projectsConfigured

        // TODO: figure out why applied scripts do not correspond
//        // Scripts - one or more for settings, and one for each project build script
//        def scripts = buildOperations.all(ApplyScriptPluginBuildOperationType)
//        assert scripts.size() >= projectsConfigured
//        // TODO: why no settings script?
////        def sortedScripts = scripts.toSorted { it -> it.startTime }
////        assert sortedScripts.first().details.targetType == "settings"
//        def nonSettingsScripts = scripts.findAll { it.details.targetType != "settings" }
//        def nonSettingsScriptTargets = nonSettingsScripts.collect { fullPath(it.details.buildPath, it.details.targetPath) }.toSet()
//        assert nonSettingsScriptTargets.size() == projectsConfigured
    }

    private void assertNoModelsQueried() {
        def modelRequestCount = modelRequests()
        assert modelRequestCount == 0
    }

    private void assertModelsQueried() {
        def modelRequestCount = modelRequests()
        assert modelRequestCount > 0
    }

    private int modelRequests() {
        buildOperations.all(QueryToolingModelBuildOperationType).size()
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

    trait HasIntermediateDetails extends HasBuildActions {
        int projectConfigured = 0
    }

    static class StoreDetails extends ConfigurationCacheFixture.StateStoreDetails implements HasIntermediateDetails {
        StoreDetails() {
            runsTasks = false
            loadsOnStore = false
        }
    }

    static class StateStoreWithProblemsDetails extends ConfigurationCacheFixture.StateStoreWithProblemsDetails implements HasIntermediateDetails {
        StateStoreWithProblemsDetails() {
            runsTasks = false
            loadsOnStore = false
        }
    }

    static class StateDiscardedWithProblemsDetails extends ConfigurationCacheFixture.StateDiscardedWithProblemsDetails implements HasIntermediateDetails {
        StateDiscardedWithProblemsDetails() {
            runsTasks = false
            loadsOnStore = false
        }
    }

    static class StoreRecreateDetails extends ConfigurationCacheFixture.StateRecreateDetails implements HasIntermediateDetails {
        StoreRecreateDetails() {
            runsTasks = false
            loadsOnStore = false
        }
    }
}
