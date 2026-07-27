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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.configurations.ResolutionHost
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactSelectionSpec
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSetResolver
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.VisitedArtifactSet
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.DefaultVisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.results.VisitedGraphResults
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructure
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.GraphStructureBuilder
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedDependencyGraph
import org.gradle.api.internal.attributes.ImmutableAttributes
import org.gradle.internal.component.external.model.ImmutableCapabilities
import org.gradle.internal.operations.TestBuildOperationExecutor
import spock.lang.Specification

import java.util.function.Supplier
import java.util.stream.Stream

class DefaultLenientConfigurationTest extends Specification {

    Supplier<GraphStructure> resultsLoader = Mock()
    def artifactSet = Stub(VisitedArtifactSet)

    def "should resolve first level dependencies in tree"() {
        given:
        def lenientConfiguration = newConfiguration()
        def graph = generateStructure([0: [1, 2, 3]])

        when:
        def results = lenientConfiguration.getFirstLevelModuleDependencies()

        then:
        results.size() == 3

        and:
        1 * resultsLoader.get() >> graph
    }

    def "should flatten all resolved dependencies in dependency tree"() {
        given:
        def lenientConfiguration = newConfiguration()
        def graph = generateStructure(treeStructure)

        when:
        def result = lenientConfiguration.getAllModuleDependencies()

        then:
        result.size() == (graph.nodes().count() - 1)
        1 * resultsLoader.get() >> graph

        where:
        treeStructure << [
            [0: [1, 2, 3, 4, 5], 5: [6, 7, 8]],
            [0: [1, 2, 3, 4, 5], 5: [6, 7, 8], 7: [9, 10], 9: [11, 12]]
        ]
    }

    private DefaultLenientConfiguration newConfiguration() {
        ResolvedDependencyGraph graph = new ResolvedDependencyGraph(ImmutableAttributes.EMPTY, () -> Stub(GraphStructure), null)
        VisitedGraphResults visitedGraphResults = new DefaultVisitedGraphResults(graph, [] as Set)
        new DefaultLenientConfiguration(Stub(ResolutionHost), visitedGraphResults, artifactSet, resultsLoader, Mock(ResolvedArtifactSetResolver), Mock(ArtifactSelectionSpec), new TestBuildOperationExecutor())
    }

    def generateStructure(LinkedHashMap<Integer, List<Integer>> treeStructure) {
        GraphStructureBuilder builder = new GraphStructureBuilder()
        builder.start(treeStructure.entrySet().first().key)

        def allNodes = treeStructure.entrySet().stream()
            .flatMap(entry -> Stream.concat(Stream.of(entry.getKey()), entry.getValue().stream()))
            .distinct()
            .sorted()
            .toList()

        assert allNodes.first() == 0
        assert allNodes.last() == allNodes.size() - 1

        allNodes.each {
            ModuleVersionIdentifier mvid = DefaultModuleVersionIdentifier.newId("g", Integer.toString(it), "v")
            builder.addComponent(it, ComponentSelectionReasons.requested(), null, Mock(ComponentIdentifier), mvid)
            builder.addNode(it, it, ImmutableAttributes.EMPTY, ImmutableCapabilities.EMPTY, "", -1)
            treeStructure.getOrDefault(it, Collections.emptyList()).each {
                builder.addSuccessfulEdge(Mock(ComponentSelector), false, it)
            }
        }
        return builder.build()
    }

}
