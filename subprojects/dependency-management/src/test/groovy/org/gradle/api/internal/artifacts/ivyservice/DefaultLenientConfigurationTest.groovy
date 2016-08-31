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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.TransientConfigurationResults
import org.gradle.api.specs.Spec
import org.gradle.internal.Factory
import spock.lang.Specification

class DefaultLenientConfigurationTest extends Specification {
    def "should resolve first level dependencies in tree"() {
        given:
        TransientConfigurationResults transientConfigurationResults = Mock(TransientConfigurationResults)
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(null, null, null, null, { transientConfigurationResults } as Factory)
        ResolvedDependency root = Mock(ResolvedDependency)
        def expectedResults = [Mock(ResolvedDependency)] as Set

        when:
        def results = lenientConfiguration.getFirstLevelModuleDependencies()

        then:
        1 * transientConfigurationResults.getRoot() >> root
        1 * root.getChildren() >> expectedResults
        results == expectedResults
        0 * _._
    }

    def "should resolve and filter first level dependencies in tree"() {
        given:
        TransientConfigurationResults transientConfigurationResults = Mock(TransientConfigurationResults)
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(null, null, null, null, { transientConfigurationResults } as Factory)
        Spec spec = Mock(Spec)
        def firstLevelDependencies = [(Mock(ModuleDependency)): Mock(ResolvedDependency), (Mock(ModuleDependency)): Mock(ResolvedDependency), (Mock(ModuleDependency)): Mock(ResolvedDependency)]
        def firstLevelDependenciesEntries = firstLevelDependencies.entrySet() as List

        when:
        def result = lenientConfiguration.getFirstLevelModuleDependencies(spec)

        then:
        1 * transientConfigurationResults.getFirstLevelDependencies() >> firstLevelDependencies
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[0].key) >> true
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[1].key) >> false
        1 * spec.isSatisfiedBy(firstLevelDependenciesEntries[2].key) >> true
        result == [firstLevelDependenciesEntries[0].value, firstLevelDependenciesEntries[2].value] as Set
        0 * _._
    }

    def "should flatten all resolved dependencies in dependency tree"() {
        given:
        TransientConfigurationResults transientConfigurationResults = Mock(TransientConfigurationResults)
        DefaultLenientConfiguration lenientConfiguration = new DefaultLenientConfiguration(null, null, null, null, { transientConfigurationResults } as Factory)

        def (expected, root) = generateDependenciesWithChildren(treeStructure)

        when:
        def result = lenientConfiguration.getAllModuleDependencies()

        then:
        1 * transientConfigurationResults.getRoot() >> root
        result.size() == size
        result == expected

        where:
        treeStructure                                               | size
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8]]                          | 8
        [0: [1, 2, 3, 4, 5], 5: [6, 7, 8], 7: [9, 10], 9: [11, 12]] | 12
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
                if(child == null) {
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
        Set<ResolvedDependency> children
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
