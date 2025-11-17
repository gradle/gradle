/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder

import com.google.common.collect.ImmutableSet
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import spock.lang.Specification

class NodeStateTest extends Specification {
    def resolveState = Stub(ResolveState) {
        getEdgeFilter() >> Specs.satisfyAll()
        getDependencySubstitutionApplicator() >> DependencySubstitutionApplicator.NO_OP
    }

    int idIdx = 0

    def "uses empty strict version constraints object if no strict versions are endorsed"() {
        given:
        def root = root()
        def platform = node()
        endorsingEdge(root, platform)
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == StrictVersionConstraints.EMPTY
        platform.ownStrictVersions == StrictVersionConstraints.EMPTY
    }

    def "collect one endorsed strict version"() {
        given:
        def root = root()
        def platform = node(["a"])
        endorsingEdge(root, platform)
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == modules(["a"])
        platform.ownStrictVersions.is(ancestorsStrictVersions)
    }

    def "collects multiple endorsed strict versions from one module"() {
        given:
        def root = root()
        def platform = node(["a", "b", "c", "d", "e", "f", "g"])
        endorsingEdge(root, platform)
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == modules(["a", "b", "c", "d", "e", "f", "g"])
        platform.ownStrictVersions.is(ancestorsStrictVersions)
    }

    def "collects endorsed strict versions from multiple sources"() {
        given:
        def root = root()
        def platform1 = node(["a"])
        def platform2 = node(["b", "c", "d", "e"])
        def otherNode = node()
        endorsingEdge(root, platform1)
        endorsingEdge(root, platform2)
        edge(root, otherNode)
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == modules(["a", "b", "c", "d", "e"])
        platform1.ownStrictVersions == modules(["a"])
        platform2.ownStrictVersions == modules(["b", "c", "d", "e"])
    }

    def "uses empty strict version constraints object if there are no ancestor constraints"() {
        given:
        def root = root()
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        root.ownStrictVersions == StrictVersionConstraints.EMPTY
        ancestorsStrictVersions == StrictVersionConstraints.EMPTY
    }

    def "collects ancestor strict versions from one parent"() {
        given:
        def root = root(["a", "b", "c", "d"])
        def child = node()
        edge(root, child)

        when:
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == modules(["a", "b", "c", "d"])
        root.ownStrictVersions.is(ancestorsStrictVersions)
    }

    def "uses empty strict version constraints object if constraints of second parent are empty"() {
        given:
        def root = root()
        def parent1 = node(["a"])
        def parent2 = node()
        edge(root, parent1)
        edge(root, parent2)

        def child = node()
        edge(parent1, child)
        edge(parent2, child)

        when:
        visit(parent1)
        visit(parent2)
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == StrictVersionConstraints.EMPTY
        parent1.ownStrictVersions == modules(["a"])
        parent2.ownStrictVersions == StrictVersionConstraints.EMPTY
    }

    def "computes intersection of ancestors"() {
        given:
        def root = root(["a"])
        def parent1 = node(["b", "c"])
        def parent2 = node(["b", "d"])
        edge(root, parent1)
        edge(root, parent2)

        def child = node()
        edge(parent1, child)
        edge(parent2, child)

        when:
        visit(parent1)
        visit(parent2)
        def ancestorsStrictVersions = visit(child)

        then:
        ancestorsStrictVersions == modules(["a", "b"])
        parent1.ownStrictVersions == modules(["b", "c"])
        parent2.ownStrictVersions == modules(["b", "d"])
    }

    private EdgeState edge(NodeState from, NodeState to) {
        edge(from, to, false)
    }

    private EdgeState endorsingEdge(NodeState from, NodeState to) {
        edge(from, to, true)
    }

    private EdgeState edge(NodeState from, NodeState to, boolean endorsing) {
        DependencyMetadata dependencyMetadata = Mock(DependencyMetadata) {
            isEndorsingStrictVersions() >> endorsing
        }
        DependencyState dependencyState = Mock(DependencyState) {
            getDependency() >> dependencyMetadata
        }
        EdgeState edge = Mock(EdgeState) {
            getFrom() >> from
            getTargetComponent() >> to.component
            getTargetNodes() >> [to]
            getDependencyState() >> dependencyState
            getDependencyMetadata() >> dependencyMetadata
        }

        to.addIncomingEdge(edge)
        from.outgoingEdges.add(edge)

        edge
    }

    def root(List<String> strictDependencies = []) {
        def root = node(strictDependencies)
        visit(root)
        root
    }

    StrictVersionConstraints visit(NodeState node) {
        node.collectOwnStrictVersions(new ModuleExclusions().nothing())
        node.previousAncestorsStrictVersions = node.collectAncestorsStrictVersions()
        node.previousAncestorsStrictVersions
    }

    private NodeState node(List<String> strictDependencies = []) {
        def state = Stub(VariantGraphResolveState) {
            getDependencies() >> strictDependencies.collect { dep ->
                Mock(DependencyMetadata) {
                    getSelector() >> DefaultModuleComponentSelector.newSelector(
                        DefaultModuleIdentifier.newId("org", dep),
                        DefaultImmutableVersionConstraint.strictly("1.0")
                    )
                }
            }
        }

        def component = Stub(ComponentState)
        def node = new NodeState(idIdx++, component, resolveState, state, true)
        component.nodes >> [node]
        node
    }

    private static StrictVersionConstraints modules(List<String> names) {
        StrictVersionConstraints.of(ImmutableSet.copyOf(
            names.collect { DefaultModuleIdentifier.newId("org", it) }
        ))
    }

}
