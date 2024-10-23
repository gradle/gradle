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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints
import org.gradle.api.specs.Spec
import org.gradle.internal.component.model.DependencyMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import spock.lang.Specification

class NodeStateTest extends Specification {

    int idIdx = 0
    NodeState root = nextNode()

    def "uses empty strict version constraints object if no strict versions are defined"() {
        Set<ModuleIdentifier> constraintsSet = []

        when:
        root.maybeCollectStrictVersions(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root, false).dependencyState)
        root.storeOwnStrictVersions(constraintsSet)

        then:
        root.ownStrictVersionConstraints == StrictVersionConstraints.EMPTY
        root.ownStrictVersionConstraints.getModules().isEmpty()
    }

    def "collects strict version constraints"() {
        Set<ModuleIdentifier> constraintsSet = []

        when:
        root.maybeCollectStrictVersions(constraintsSet, edge(root).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root).dependencyState)
        root.maybeCollectStrictVersions(constraintsSet, edge(root, false).dependencyState)
        root.storeOwnStrictVersions(constraintsSet)

        then:
        root.ownStrictVersionConstraints.getModules().size() == 3
        root.ownStrictVersionConstraints.getModules() == constraintsSet
    }

    def "uses empty strict version constraints object if no strict versions are endorsed"() {
        when:
        def endorseFrom = edge(root, false, null, nextNode(0), true)
        root.collectEndorsedStrictVersions([endorseFrom])
        def endorsedStrictVersions = root.getEndorsedStrictVersions(edge(root))

        then:
        endorsedStrictVersions == StrictVersionConstraints.EMPTY
        endorseFrom.targetComponent.nodes[0].ownStrictVersionConstraints == StrictVersionConstraints.EMPTY
    }

    def "collect one endorsed strict version"() {
        when:
        def endorseFrom = edge(root, false, null, nextNode(1), true)
        root.collectEndorsedStrictVersions([endorseFrom])
        def endorsedStrictVersions = root.getEndorsedStrictVersions(edge(root, false))

        then:
        endorsedStrictVersions.getModules().size() == 1
        endorseFrom.targetComponent.nodes[0].ownStrictVersionConstraints.getModules().size() == 1
        endorseFrom.targetComponent.nodes[0].ownStrictVersionConstraints == endorsedStrictVersions //instance reused
    }

    def "collects multiple endorsed strict versions from one module"() {
        when:
        def endorseFrom = edge(root, false, null, nextNode(12), true)
        root.collectEndorsedStrictVersions([endorseFrom])
        def endorsedStrictVersions = root.getEndorsedStrictVersions(edge(root, false))

        then:
        endorsedStrictVersions.getModules().size() == 12
        endorseFrom.targetComponent.nodes[0].ownStrictVersionConstraints.getModules().size() == 12
        endorseFrom.targetComponent.nodes[0].ownStrictVersionConstraints == endorsedStrictVersions //instance reused
    }

    def "collects endorsed strict versions from multiple sources"() {
        when:
        edge(root, false) // not-endorsing edge
        def endorseFrom1 = edge(root, false, null, nextNode(1), true)
        def endorseFrom2 = edge(root, false, null, nextNode(4), true)
        root.collectEndorsedStrictVersions([endorseFrom1, endorseFrom2])
        def endorsedStrictVersions = root.getEndorsedStrictVersions(edge(root, false))

        then:
        def modulesAll = endorsedStrictVersions.getModules()
        def modules1 = endorseFrom1.targetComponent.nodes[0].ownStrictVersionConstraints.getModules()
        def modules2 = endorseFrom2.targetComponent.nodes[0].ownStrictVersionConstraints.getModules()

        modulesAll.size() == 5
        modules1.size() == 1
        modules2.size() == 4

        modulesAll == modules1 + modules2

        endorseFrom1.targetComponent.nodes[0].ownStrictVersionConstraints != endorsedStrictVersions
        endorseFrom2.targetComponent.nodes[0].ownStrictVersionConstraints != endorsedStrictVersions
    }

    def "uses empty strict version constraints object if there are no ancestor constraints"() {
        when:
        def edge = edge(root)
        def childNode = edge.targetComponent.nodes[0]
        collectOwnStrictVersions(root, [])
        childNode.collectAncestorsStrictVersions([edge])

        then:
        root.ownStrictVersionConstraints == StrictVersionConstraints.EMPTY
        childNode.ancestorsStrictVersionConstraints == StrictVersionConstraints.EMPTY
    }

    def "collects ancestor strict versions from one parent"() {
        when:
        def edge = edge(root, false)
        def childNode = edge.targetComponent.nodes[0]
        collectOwnStrictVersions(root, ["a", "b", "c", "d"]) // this is always expected to run before running collectAncestorsStrictVersions() on a child

        childNode.collectAncestorsStrictVersions([edge])

        then:
        root.ownStrictVersionConstraints.getModules().size() == 4
        childNode.ancestorsStrictVersionConstraints.getModules().size() == 4
        childNode.ancestorsStrictVersionConstraints == root.ownStrictVersionConstraints //instance reused
    }

    def "uses empty strict version constraints object if constraints of second parent are empty"() {
        when:
        def parent1 = edge(root, false).targetComponent.nodes[0]
        def parent2 = edge(root, false).targetComponent.nodes[0]
        collectOwnStrictVersions(parent1, ['a'])
        collectOwnStrictVersions(parent2, [])

        def childNode = nextNode()
        def edge1 = edge(parent1, false, null, childNode)
        def edge2 = edge(parent2, false, null, childNode)
        childNode.collectAncestorsStrictVersions([edge1, edge2])

        then:
        childNode.ancestorsStrictVersionConstraints == StrictVersionConstraints.EMPTY
        parent1.ownStrictVersionConstraints != StrictVersionConstraints.EMPTY
        parent2.ownStrictVersionConstraints == StrictVersionConstraints.EMPTY
    }

    def "computes intersection of ancestors"() {
        when:
        def parent1Edge = edge(root, false)
        def parent2Edge = edge(root, false)
        def parent1 = parent1Edge.targetComponent.nodes[0]
        def parent2 = parent2Edge.targetComponent.nodes[0]
        collectOwnStrictVersions(root, ['a']) //root, on all paths
        collectOwnStrictVersions(parent1, ['b', 'c'])
        collectOwnStrictVersions(parent2, ['b', 'd'])

        def child = nextNode()
        def edge1 = edge(parent1, false, null, child)
        def edge2 = edge(parent2, false, null, child)
        parent1.collectAncestorsStrictVersions([parent1Edge])
        parent2.collectAncestorsStrictVersions([parent2Edge])
        child.collectAncestorsStrictVersions([edge1, edge2])

        then:
        child.ancestorsStrictVersionConstraints.getModules() == moduleSet('a', 'b')
        parent1.ancestorsStrictVersionConstraints == root.ownStrictVersionConstraints
        parent2.ancestorsStrictVersionConstraints == root.ownStrictVersionConstraints
    }

    private collectOwnStrictVersions(NodeState node, List<String> children) {
        Set<ModuleIdentifier> constraintsSet = []
        for (String child : children) {
            node.maybeCollectStrictVersions(constraintsSet, edge(node, true, child, nextNode(), true).dependencyState)
        }
        node.storeOwnStrictVersions(constraintsSet)
    }

    private EdgeState edge(NodeState from, boolean strict = true, String name = null, NodeState to = nextNode(), endorsing = false) {
        String moduleName = name ? name : "${from.nodeId}-${to.nodeId}"
        EdgeState edgeState = Mock()
        DependencyState dependencyState = Mock()
        DependencyMetadata dependencyMetadata = Mock()
        ModuleComponentSelector selector = Mock()
        ImmutableVersionConstraint versionConstraint = Mock()
        ComponentState componentState = Mock()
        edgeState.from >> from
        edgeState.getTargetComponent() >> componentState
        componentState.nodes >> [to]
        edgeState.dependencyState >> dependencyState
        edgeState.dependencyMetadata >> dependencyMetadata
        dependencyState.dependency >> dependencyMetadata
        dependencyMetadata.selector >> selector
        dependencyMetadata.endorsingStrictVersions >> endorsing
        selector.versionConstraint >> versionConstraint
        selector.moduleIdentifier >> DefaultModuleIdentifier.newId("org", moduleName)
        versionConstraint.strictVersion >> (strict ? "1.0" : "")
        edgeState
    }

    private NodeState nextNode(int outgoingEndorsing = 0) {
        def state = Stub(VariantGraphResolveState)
        def resolveState = Stub(ResolveState)

        def newState = new NodeState(idIdx++, Stub(ComponentState), resolveState, state, true)
        // if there are outgoing endorsing edges, also include a normal edge to make sure that it is filtered out
        state.dependencies >> ((0..<outgoingEndorsing).collect { edge(newState).dependencyMetadata } + (outgoingEndorsing > 0 ? [edge(newState, false).dependencyMetadata] : []))
        resolveState.moduleExclusions >> Mock(ModuleExclusions)
        resolveState.edgeFilter >> new Spec<DependencyMetadata>() {
            boolean isSatisfiedBy(DependencyMetadata element) { true }
        }
        newState
    }

    private static Set moduleSet(String... names) {
        names.collect { DefaultModuleIdentifier.newId('org', it) }
    }
}
