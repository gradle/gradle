/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice

import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.DependencyGraphNodeResult
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactsResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedFileDependencyResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResultsLoader
import org.gradle.api.internal.artifacts.transform.ArtifactTransforms
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.api.specs.Spec
import org.gradle.internal.operations.BuildOperationExecutor
import org.gradle.test.fixtures.work.TestWorkerLeaseService
import spock.lang.Specification

class DefaultLenientConfigurationTest extends Specification {
    def transforms = Stub(ArtifactTransforms)
    def transientConfigurationResults = Mock(TransientConfigurationResults)
    def resultsLoader = Mock(TransientConfigurationResultsLoader)
    def artifactsResults = Stub(VisitedArtifactsResults)
    def fileDependencyResults = Stub(VisitedFileDependencyResults)
    def configuration = Stub(ConfigurationInternal)
    def buildOperationExecutor = Mock(BuildOperationExecutor)
    def dependencyVerificationOverride = DependencyVerificationOverride.NO_VERIFICATION

    def setup() {
        _ * configuration.attributes >> ImmutableAttributes.EMPTY
    }

    def "should resolve first level dependencies in tree"() {
        given:

        def rootNode = new TestResolvedDependency()
        def child = new TestResolvedDependency()
        rootNode.children.add(child)
        def expectedResults = [child] as Set

        def lenientConfiguration = newConfiguration()

        when:
        def results = lenientConfiguration.getFirstLevelModuleDependencies()

        then:
        results == expectedResults

        and:
        1 * resultsLoader.create(_) >> transientConfigurationResults
        1 * transientConfigurationResults.getFirstLevelDependencies() >> [(Mock(ModuleDependency)): child]
        0 * _
    }

    def "should resolve and filter first level dependencies in tree"() {
        given:
        def spec = Mock(Spec)
        def node1 = new TestResolvedDependency()
        def node2 = new TestResolvedDependency()
        def node3 = new TestResolvedDependency()
        def firstLevelDependencies = [(Mock(ModuleDependency)): node1, (Mock(ModuleDependency)): node2, (Mock(ModuleDependency)): node3]
        def firstLevelDependenciesEntries = firstLevelDependencies.entrySet() as List

        def lenientConfiguration = newConfiguration()

        when:
        def result = lenientConfiguration.getFirstLevelModuleDependencies(spec)

        then:
        result == [node1, node3] as Set

        1 * resultsLoader.create(_) >> transientConfigurationResults
        1 * transientConfigurationResults.getFirstLevelDependencies() >> firstLevelDependencies
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[0].key) >> true
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[1].key) >> false
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[2].key) >> true
        0 * _
    }

    def "should flatten all resolved dependencies in dependency tree"() {
        given:
        def lenientConfiguration = newConfiguration()

        def (expected, root) = generateDependenciesWithChildren(treeStructure)

        when:
        def result = lenientConfiguration.getAllModuleDependencies()

        then:
        result.size() == size
        result == expected

        1 * resultsLoader.create(_) >> transientConfigurationResults
        1 * transientConfigurationResults.getRootNode() >> root

        where:
        treeStructure                                               | size
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8]]                          | 8
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8], 7: [9, 10], 9: [11, 12]] | 12
    }

    private DefaultLenientConfiguration newConfiguration() {
        new DefaultLenientConfiguration(configuration, false, null, artifactsResults, fileDependencyResults, resultsLoader, transforms, buildOperationExecutor, dependencyVerificationOverride, new TestWorkerLeaseService())
    }

    def generateDependenciesWithChildren(Map treeStructure) {
        Map<Integer, TestResolvedDependency> dependenciesById = [:]
        for (Map.Entry entry : treeStructure.entrySet()) {
            Integer id = entry.getKey() as Integer
            dependenciesById.put(id, new TestResolvedDependency())
        }
        for (Map.Entry entry : treeStructure.entrySet()) {
            Integer id = entry.getKey() as Integer
            dependenciesById.get(id).children = entry.getValue().collect {
                def child = dependenciesById.get(it)
                if (child == null) {
                    child = new TestResolvedDependency()
                    dependenciesById.put(it, child)
                }
                child
            } as Set
        }
        def root = dependenciesById.remove(0)
        [new LinkedHashSet(dependenciesById.values()), root]
    }

    private static class TestResolvedDependency implements ResolvedDependency, DependencyGraphNodeResult {
        String name
        String moduleGroup
        String moduleName
        String moduleVersion
        String configuration
        ResolvedModuleVersion module
        Set<ResolvedDependency> children = []
        Set<ResolvedDependency> parents
        Set<ResolvedArtifact> moduleArtifacts
        Set<ResolvedArtifact> allModuleArtifacts

        @Override
        ResolvedDependency getPublicView() {
            return this
        }

        @Override
        Collection<? extends DependencyGraphNodeResult> getOutgoingEdges() {
            throw new UnsupportedOperationException()
        }

        @Override
        ResolvedArtifactSet getArtifactsForNode() {
            throw new UnsupportedOperationException()
        }

        @Override
        Set<ResolvedArtifact> getParentArtifacts(ResolvedDependency parent) {
            return null
        }

        @Override
        Set<ResolvedArtifact> getArtifacts(ResolvedDependency parent) {
            return null
        }

        @Override
        Set<ResolvedArtifact> getAllArtifacts(ResolvedDependency parent) {
            return null
        }
    }
}
