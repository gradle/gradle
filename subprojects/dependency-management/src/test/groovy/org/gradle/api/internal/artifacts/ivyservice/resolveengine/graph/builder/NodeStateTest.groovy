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
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.subgraphconstraints.SubgraphConstraints
import org.gradle.api.specs.Spec
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DependencyMetadata
import spock.lang.Specification

class NodeStateTest extends Specification {

    int idIdx = 0
    NodeState root = nextNode()

    def "uses empty subgraph constraints object if no subgraph constraints are defined"() {
        Set<ModuleIdentifier> constraintsSet = []

        when:
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root, false).dependencyState)
        root.storeOwnConstraints(constraintsSet)

        then:
        root.ownSubgraphConstraints == SubgraphConstraints.EMPTY
        root.ownSubgraphConstraints.getModules().isEmpty()
    }

    def "collects subgraph constraints"() {
        Set<ModuleIdentifier> constraintsSet = []

        when:
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root, false).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root).dependencyState)
        root.maybeCollectSubgraphConstraint(constraintsSet, edge(root, false).dependencyState)
        root.storeOwnConstraints(constraintsSet)

        then:
        root.ownSubgraphConstraints.getModules().size() == 3
        root.ownSubgraphConstraints.getModules() == constraintsSet
    }

    def "uses empty subgraph constraints object if no subgraph constraints are inherited"() {
        when:
        def inheritFrom = edge(root, false, null, nextNode(0), true)
        root.collectInheritedSubgraphConstraints([inheritFrom])
        def inheritedConstraints = root.getInheritedSubgraphConstraints(edge(root, false))

        then:
        inheritedConstraints == SubgraphConstraints.EMPTY
        inheritFrom.targetComponent.nodes[0].ownSubgraphConstraints == SubgraphConstraints.EMPTY
    }

    def "collect one inherited subgraph constraint"() {
        when:
        def inheritFrom = edge(root, false, null, nextNode(1), true)
        root.collectInheritedSubgraphConstraints([inheritFrom])
        def inheritedConstraints = root.getInheritedSubgraphConstraints(edge(root, false))

        then:
        inheritedConstraints.getModules().size() == 1
        inheritFrom.targetComponent.nodes[0].ownSubgraphConstraints.getModules().size() == 1
        inheritFrom.targetComponent.nodes[0].ownSubgraphConstraints == inheritedConstraints //instance reused
    }

    def "collects multiple inherited subgraph constraints from one module"() {
        when:
        def inheritFrom = edge(root, false, null, nextNode(12), true)
        root.collectInheritedSubgraphConstraints([inheritFrom])
        def inheritedConstraints = root.getInheritedSubgraphConstraints(edge(root, false))

        then:
        inheritedConstraints.getModules().size() == 12
        inheritFrom.targetComponent.nodes[0].ownSubgraphConstraints.getModules().size() == 12
        inheritFrom.targetComponent.nodes[0].ownSubgraphConstraints == inheritedConstraints //instance reused
    }

    def "collects inherited subgraph constraints from multiple sources"() {
        when:
        edge(root, false) // not-inheriting edge
        def inheritFrom1 = edge(root, false, null, nextNode(1), true)
        def inheritFrom2 = edge(root, false, null, nextNode(4), true)
        root.collectInheritedSubgraphConstraints([inheritFrom1, inheritFrom2])
        def inheritedConstraints = root.getInheritedSubgraphConstraints(edge(root, false))

        then:
        def modulesAll = inheritedConstraints.getModules()
        def modules1 = inheritFrom1.targetComponent.nodes[0].ownSubgraphConstraints.getModules()
        def modules2 = inheritFrom2.targetComponent.nodes[0].ownSubgraphConstraints.getModules()

        modulesAll.size() == 5
        modules1.size() == 1
        modules2.size() == 4

        modulesAll == modules1 + modules2

        inheritFrom1.targetComponent.nodes[0].ownSubgraphConstraints != inheritedConstraints
        inheritFrom2.targetComponent.nodes[0].ownSubgraphConstraints != inheritedConstraints
    }

    def "uses empty subgraph constraints object if there are no ancestor constraints"() {
        when:
        def edge = edge(root, false)
        def childNode = edge.targetComponent.nodes[0]
        collectOwnSubgraphConstraints(root, [])
        childNode.collectAncestorsSubgraphConstraints([edge])

        then:
        root.ownSubgraphConstraints == SubgraphConstraints.EMPTY
        childNode.ancestorsSubgraphConstraints == SubgraphConstraints.EMPTY
    }

    def "collects ancestor subgraph constraints from one parent"() {
        when:
        def edge = edge(root, false)
        def childNode = edge.targetComponent.nodes[0]
        collectOwnSubgraphConstraints(root, ["a", "b", "c", "d"]) // this is always expected to run before running collectAncestorsSubgraphConstraints() on a child

        childNode.collectAncestorsSubgraphConstraints([edge])

        then:
        root.ownSubgraphConstraints.getModules().size() == 4
        childNode.ancestorsSubgraphConstraints.getModules().size() == 4
        childNode.ancestorsSubgraphConstraints == root.ownSubgraphConstraints //instance reused
    }

    def "uses empty subgraph constraints object if constraints of second parent are empty"() {
        when:
        def parent1 = edge(root, false).targetComponent.nodes[0]
        def parent2 = edge(root, false).targetComponent.nodes[0]
        collectOwnSubgraphConstraints(parent1, ['a'])
        collectOwnSubgraphConstraints(parent2, [])

        def childNode = nextNode()
        def edge1 = edge(parent1, false, null, childNode)
        def edge2 = edge(parent2, false, null, childNode)
        childNode.collectAncestorsSubgraphConstraints([edge1, edge2])

        then:
        childNode.ancestorsSubgraphConstraints == SubgraphConstraints.EMPTY
        parent1.ownSubgraphConstraints != SubgraphConstraints.EMPTY
        parent2.ownSubgraphConstraints == SubgraphConstraints.EMPTY
    }

    def "computes intersection of ancestors"() {
        when:
        def parent1Edge = edge(root, false)
        def parent2Edge = edge(root, false)
        def parent1 = parent1Edge.targetComponent.nodes[0]
        def parent2 = parent2Edge.targetComponent.nodes[0]
        collectOwnSubgraphConstraints(root, ['a']) //root, on all paths
        collectOwnSubgraphConstraints(parent1, ['b', 'c'])
        collectOwnSubgraphConstraints(parent2, ['b', 'd'])

        def child = nextNode()
        def edge1 = edge(parent1, false, null, child)
        def edge2 = edge(parent2, false, null, child)
        parent1.collectAncestorsSubgraphConstraints([parent1Edge])
        parent2.collectAncestorsSubgraphConstraints([parent2Edge])
        child.collectAncestorsSubgraphConstraints([edge1, edge2])

        then:
        child.ancestorsSubgraphConstraints.getModules() == moduleSet('a', 'b')
        parent1.ancestorsSubgraphConstraints == root.ownSubgraphConstraints
        parent2.ancestorsSubgraphConstraints == root.ownSubgraphConstraints
    }

    private collectOwnSubgraphConstraints(NodeState node, List<String> children) {
        Set<ModuleIdentifier> constraintsSet = []
        for (String child : children) {
            node.maybeCollectSubgraphConstraint(constraintsSet, edge(node, true, child).dependencyState)
        }
        node.storeOwnConstraints(constraintsSet)
    }

    private EdgeState edge(NodeState from, boolean forSubgraph = true, String name = null, NodeState to = nextNode(), inheriting = false) {
        String moduleName = name ? name : "${from.nodeId}-${to.nodeId}"
        EdgeState edgeState = Mock()
        DependencyState dependencyState = Mock()
        DependencyMetadata dependencyMetadata = Mock()
        ModuleComponentSelector selector = Mock()
        VersionConstraint versionConstraint = Mock()
        ComponentState componentState = Mock()
        edgeState.from >> from
        edgeState.getTargetComponent() >> componentState
        componentState.nodes >> [to]
        edgeState.dependencyState >> dependencyState
        edgeState.dependencyMetadata >> dependencyMetadata
        dependencyState.dependency >> dependencyMetadata
        dependencyMetadata.selector >> selector
        dependencyMetadata.inheriting >> inheriting
        selector.versionConstraint >> versionConstraint
        selector.moduleIdentifier >> DefaultModuleIdentifier.newId("org", moduleName)
        versionConstraint.forSubgraph >> forSubgraph
        edgeState
    }

    private NodeState nextNode(int outgoingSubgraph = 0) {
        def metadata = Stub(ConfigurationMetadata)
        def resolveState = Stub(ResolveState)
        def newState = new NodeState(idIdx++, null, Mock(ComponentState), resolveState, metadata)
        // if outgoing subgraph edges, also include a normal edge to make sure that is filtered out
        metadata.dependencies >> ((0..<outgoingSubgraph).collect { edge(newState).dependencyMetadata } + (outgoingSubgraph > 0 ? [edge(newState, false).dependencyMetadata] : []))
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
