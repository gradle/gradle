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
import org.gradle.api.artifacts.result.ResolvedComponentResult
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
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
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

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.CONFLICT_RESOLUTION
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.of
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.requested
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.root
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultPrinter.printGraph

class StreamingResolutionResultBuilderTest extends Specification {

    AdhocHandlingComponentResultSerializer componentResultSerializer = new AdhocHandlingComponentResultSerializer(
        new ThisBuildTreeOnlyComponentResultSerializer(
            DependencyManagementTestUtil.componentSelectionDescriptorFactory()
        ),
        new CompleteComponentResultSerializer(
            DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
            new DefaultImmutableModuleIdentifierFactory(),
            AttributeTestUtil.attributesFactory(),
            TestUtil.objectInstantiator()
        )
    )

    class DummyStore implements Store<ResolvedComponentResult> {
        ResolvedComponentResult load(Supplier<ResolvedComponentResult> createIfNotPresent) {
            return createIfNotPresent.get()
        }
    }

    def builder = new StreamingResolutionResultBuilder(
        new DummyBinaryStore(),
        new DummyStore(),
        new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator()),
        new CapabilitySelectorSerializer(),
        componentResultSerializer,
        DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
        false
    )

    def "result can be read multiple times"() {
        def rootNode = rootNode(1, "org", "root", "1.0")
        builder.start(rootNode)
        builder.visitNode(rootNode)
        builder.finish(rootNode)

        when:
        def result = builder.getResolutionResult([] as Set)

        then:
        with(result.rootSource.get()) {
            id == DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("org", "root"), "1.0")
            selectionReason == root()
        }
        printGraph(result.rootSource.get()) == """org:root:1.0
"""
    }

    def "maintains graph in byte stream"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector1 = selector("org", "dep1", "2.0")
        def selector2 = selector("org", "dep2", "3.0")
        def dep1 = node(2, "org", "dep1", "2.0", of(CONFLICT_RESOLUTION))
        root.outgoingEdges >> [
                dep(selector1, 1, 2),
                dep(selector2, 1, new RuntimeException("Boo!"))
        ]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(dep1)
        builder.visitEdges(root)

        builder.finish(root)

        when:
        def result = builder.getResolutionResult([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:2.0(C) [root]
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "visiting resolved module version again has no effect"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector = selector("org", "dep1", "2.0")
        root.outgoingEdges >> [dep(selector, 1, 2)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(1, "org", "root", "1.0", requested())) //it's fine

        builder.visitNode(node(2, "org", "dep1", "2.0", of(CONFLICT_RESOLUTION)))
        builder.visitNode(node(2, "org", "dep1", "2.0", requested())) //will be ignored

        builder.visitEdges(root)

        builder.finish(root)

        when:
        def result = builder.getResolutionResult([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:2.0(C) [root]
"""
    }

    def "accumulates dependencies for all configurations of same component"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector1 = selector("org", "dep1", "1.0")
        root.outgoingEdges >> [dep(selector1, 1, 2)]

        def conf1 = node(2, "org", "dep1", "1.0")
        def selector2 = selector("org", "dep2", "1.0")
        conf1.outgoingEdges >> [dep(selector2, 2, 3)]

        def conf2 = node(2, "org", "dep1", "1.0")
        def selector3 = selector("org", "dep3", "1.0")
        conf2.outgoingEdges >> [dep(selector3, 2, 4)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(conf1)
        builder.visitNode(conf2)
        builder.visitNode(node(3, "org", "dep2", "1.0"))
        builder.visitNode(node(4, "org", "dep3", "1.0"))

        builder.visitEdges(root)
        builder.visitEdges(conf1)
        builder.visitEdges(conf2)

        builder.finish(root)

        when:
        def result = builder.getResolutionResult([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:1.0 [root]
    org:dep2:1.0 [dep1]
    org:dep3:1.0 [dep1]
"""
    }

    def "dependency failures are remembered"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector1 = selector("org", "dep1", "1.0")
        def selector2 = selector("org", "dep2", "2.0")
        root.outgoingEdges >> [
            dep(selector1, 1, new RuntimeException()),
            dep(selector2, 1, 3)
        ]
        def dep2 = node(3, "org", "dep2", "2.0")
        def selector3 = selector("org", "dep1", "5.0")
        dep2.outgoingEdges >> [dep(selector3, 3, new RuntimeException())]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(2, "org", "dep1", "2.0"))

        builder.visitNode(dep2)

        builder.visitEdges(root)
        builder.visitEdges(dep2)

        builder.finish(root)

        when:
        def result = builder.getResolutionResult([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:1.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0 [root]
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, Long fromVariant, Long selectedId) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> selector.componentSelector
        _ * edge.selector >> selector
        _ * edge.selected >> selectedId
        _ * edge.failure >> null
        _ * edge.fromVariant >> fromVariant
        _ * edge.selectedVariant >> selectedId
        return edge
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, Long fromVariant, Throwable failure) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.selector >> selector
        _ * edge.requested >> selector.componentSelector
        _ * edge.reason >> requested()
        _ * edge.failure >> new ModuleVersionResolveException(selector.componentSelector, failure)
        _ * edge.fromVariant >> fromVariant
        _ * edge.selectedVariant >> null
        return edge
    }

    private DependencyGraphNode node(Long componentId, String org, String name, String ver, ComponentSelectionReason reason = requested()) {
        def componentMetadata = Stub(ComponentGraphResolveMetadata)
        _ * componentMetadata.moduleVersionId >> DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)

        def componentState = Stub(ComponentGraphResolveState)
        _ * componentState.instanceId >> componentId
        _ * componentState.id >> DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
        _ * componentState.metadata >> componentMetadata

        def variant = Stub(ResolvedGraphVariant)
        variant.nodeId >> componentId

        def component = Stub(DependencyGraphComponent)
        _ * component.resultId >> componentId
        _ * component.selectionReason >> reason
        _ * component.resolveState >> componentState
        _ * component.selectedVariants >> [variant]

        def node = Stub(DependencyGraphNode)
        _ * node.owner >> component
        return node
    }

    private RootGraphNode rootNode(Long componentId, String org, String name, String ver) {
        def componentMetadata = Stub(ComponentGraphResolveMetadata)
        _ * componentMetadata.moduleVersionId >> DefaultModuleVersionIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)

        def componentState = Stub(ComponentGraphResolveState)
        _ * componentState.id >> DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(org, name), ver)
        _ * componentState.metadata >> componentMetadata

        def variant = Stub(ResolvedGraphVariant)
        variant.nodeId >> componentId

        def component = Stub(DependencyGraphComponent)
        _ * component.resultId >> componentId
        _ * component.selectionReason >> root()
        _ * component.resolveState >> componentState
        _ * component.selectedVariants >> [variant]

        def node = Stub(RootGraphNode)
        _ * node.owner >> component
        _ * node.metadata >> Mock(LocalVariantGraphResolveMetadata) {
            getAttributes() >> AttributeTestUtil.attributes(["org.foo": "v1", "org.bar": 2, "org.baz": true])
        }
        return node
    }

    private DependencyGraphSelector selector(String org, String name, String ver) {
        def selector = Stub(DependencyGraphSelector)
        selector.componentSelector >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(org, name), new DefaultMutableVersionConstraint(ver))
        return selector
    }
}
