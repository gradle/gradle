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

import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults
import org.gradle.api.internal.artifacts.result.MinimalResolutionResult
import spock.lang.Specification

import java.util.function.Function

class DefaultLenientConfigurationTest extends Specification {

    def transientConfigurationResults = Mock(TransientConfigurationResults)
    def resultsLoader = Mock(Function)
    def artifactSet = Stub(VisitedArtifactSet)

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
        1 * resultsLoader.apply(_) >> transientConfigurationResults
        1 * transientConfigurationResults.getFirstLevelDependencies() >> ImmutableSet.of(child)
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

        1 * resultsLoader.apply(_) >> transientConfigurationResults
        1 * transientConfigurationResults.getRootNode() >> root

        where:
        treeStructure                                               | size
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8]]                          | 8
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8], 7: [9, 10], 9: [11, 12]] | 12
    }

    private DefaultLenientConfiguration newConfiguration() {
        VisitedGraphResults visitedGraphResults = new DefaultVisitedGraphResults(Stub(MinimalResolutionResult), [] as Set)
        new DefaultLenientConfiguration(Stub(ResolutionHost), visitedGraphResults, artifactSet, resultsLoader, Mock(ResolvedArtifactSetResolver), Mock(ArtifactSelectionSpec))
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

    private static class TestResolvedDependency implements ResolvedDependency {

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
