/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DependencyManagementTestUtil
import org.gradle.api.internal.artifacts.capability.CapabilitySelectorSerializer
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.cache.internal.Store
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import java.util.function.Supplier

import static GraphStructurePrinter.printGraph

class StreamingResolutionResultBuilderTest extends Specification {

    class DummyStore implements Store<GraphStructure> {
        GraphStructure load(Supplier<GraphStructure> createIfNotPresent) {
            return createIfNotPresent.get()
        }
    }

    def builder = new StreamingResolutionResultBuilder(
        new DummyBinaryStore(),
        new DummyStore(),
        new ThisBuildTreeOnlyGraphElementStore(),
        new AttributeDesugaring(AttributeTestUtil.attributesFactory()),
        new CapabilitySelectorSerializer(),
        DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
        new DefaultImmutableModuleIdentifierFactory(),
        AttributeTestUtil.attributesFactory(),
        TestUtil.objectInstantiator(),
        false
    )

    int nodeIds = 0
    int componentIds = 0

    def "result can be read multiple times"() {
        def rootNode = rootNode("org", "root", "1.0")
        builder.start(rootNode)
        builder.visitEdges(rootNode)
        builder.finish(rootNode)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        with(result.graphSource().get()) {
            def rootComponent = nodes().owner(nodes().root())
            components().id(rootComponent) == DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "root"), "1.0")
            components().selectionReason(rootComponent) == ComponentSelectionReasons.root()
        }
        printGraph(result.graphSource().get()) == """org:root:1.0
"""
    }

    def "maintains graph in byte stream"() {
        def node1 = node(component("org", "dep1", "2.0", ComponentSelectionReasons.of(ComponentSelectionReasons.CONFLICT_RESOLUTION)))
        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [
            dep(node1),
            dep(selector("org", "dep2", "3.0"), new RuntimeException("Boo!"))
        ]

        builder.start(root)

        builder.visitEdges(root)
        builder.visitEdges(node1)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:2.0(C)
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "accumulates dependencies for all configurations of same component"() {
        def node2 = node(component("org", "dep2", "1.0"))
        def node3 = node(component("org", "dep3", "1.0"))

        def comp1 = component("org", "dep1", "1.0")
        def node11 = node(comp1)
        def node12 = node(comp1)
        node11.outgoingEdges >> [dep(node2)]
        node12.outgoingEdges >> [dep(node3)]

        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [dep(node11), dep(node12)]

        builder.start(root)

        builder.visitEdges(root)
        builder.visitEdges(node11)
        builder.visitEdges(node12)
        builder.visitEdges(node2)
        builder.visitEdges(node3)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:1.0
    org:dep2:1.0
  org:dep1:1.0
    org:dep3:1.0
"""
    }

    def "dependency failures are remembered"() {
        def node2 = node(component("org", "dep2", "2.0"))
        node2.outgoingEdges >> [dep(selector("org", "dep1", "5.0"), new RuntimeException())]

        def root = rootNode("org", "root", "1.0")
        root.outgoingEdges >> [
            dep(selector("org", "dep1", "1.0"), new RuntimeException()),
            dep(node2)
        ]

        builder.start(root)

        builder.visitEdges(root)
        builder.visitEdges(node(component("org", "dep1", "2.0")))

        builder.visitEdges(node2)

        builder.finish(root)

        when:
        def result = builder.getResolvedDependencyGraph([] as Set)

        then:
        printGraph(result.graphSource().get()) == """org:root:1.0
  org:dep1:1.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }

    private DependencyGraphComponent component(String org, String name, String ver, ComponentSelectionReason reason = ComponentSelectionReasons.requested()) {
        def componentId = componentIds++
        def componentMetadata = Stub(ComponentGraphResolveMetadata) {
            getModuleVersionId() >> DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
        }

        def componentState = Stub(ComponentGraphResolveState) {
            getInstanceId() >> componentId
            getId() >> DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
            getMetadata() >> componentMetadata
        }

        return Stub(DependencyGraphComponent) {
            getResultId() >> componentId
            getSelectionReason() >> reason
            getResolveState() >> componentState
            getSelectedVariants() >> []
        }
    }

    private DependencyGraphEdge dep(DependencyGraphNode node) {
        def moduleVersionId = node.owner.resolveState.metadata.moduleVersionId
        def selector = selector(moduleVersionId.group, moduleVersionId.name, moduleVersionId.version)
        return dep(selector, node)
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, DependencyGraphNode selected) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> selector.requested
        _ * edge.selector >> selector
        _ * edge.failure >> null
        _ * edge.targetNodes >> [selected]
        return edge
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, Throwable failure) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.selector >> selector
        _ * edge.requested >> selector.requested
        _ * edge.reason >> ComponentSelectionReasons.requested()
        _ * edge.failure >> new ModuleVersionResolveException(selector.requested, failure)
        return edge
    }

    private DependencyGraphNode node(DependencyGraphComponent component) {
        int nodeId = nodeIds++
        def node = Stub(DependencyGraphNode) {
            getOwner() >> component
            getNodeId() >> nodeId
            getExternalVariant() >> null
        }
        component.selectedVariants.add(node)
        return node
    }

    private RootGraphNode rootNode(String org, String name, String ver) {
        def component = component(org, name, ver, ComponentSelectionReasons.root())
        int nodeId = nodeIds++
        def node = Stub(RootGraphNode) {
            getOwner() >> component
            getNodeId() >> nodeId
            getExternalVariant() >> null
            getMetadata() >> Mock(LocalVariantGraphResolveMetadata) {
                getAttributes() >> AttributeTestUtil.attributes(["org.foo": "v1", "org.bar": 2, "org.baz": true])
            }
        }
        component.selectedVariants.add(node)
        return node
    }

    private DependencyGraphSelector selector(String org, String name, String ver) {
        def selector = Stub(DependencyGraphSelector)
        selector.requested >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(org, name), new DefaultMutableVersionConstraint(ver))
        return selector
    }
}
