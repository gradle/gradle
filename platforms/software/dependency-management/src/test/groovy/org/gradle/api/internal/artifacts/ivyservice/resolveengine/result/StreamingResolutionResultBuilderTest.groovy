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
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.RootGraphNode
import org.gradle.api.internal.attributes.AttributeDesugaring
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.LocalConfigurationGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.util.AttributeTestUtil
import org.gradle.util.TestUtil
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.CONFLICT_RESOLUTION
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.of
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.requested
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.root
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultPrinter.printGraph

class StreamingResolutionResultBuilderTest extends Specification {
    def builder = new StreamingResolutionResultBuilder(
        new DummyBinaryStore(),
        new DummyStore(),
        new DesugaredAttributeContainerSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator()),
        new ThisBuildOnlyComponentDetailsSerializer(new DefaultImmutableModuleIdentifierFactory()),
        new ThisBuildOnlySelectedVariantSerializer(AttributeTestUtil.attributesFactory(), TestUtil.objectInstantiator()),
        new AttributeDesugaring(AttributeTestUtil.attributesFactory()),
        DependencyManagementTestUtil.componentSelectionDescriptorFactory(),
        false
    )

    def "result can be read multiple times"() {
        def rootNode = rootNode(1, "org", "root", "1.0")
        builder.start(rootNode)
        builder.visitNode(rootNode)
        builder.finish(rootNode)

        when:
        def result = builder.complete([] as Set)

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
        def selector1 = selector(1, "org", "dep1", "2.0")
        def selector2 = selector(2, "org", "dep2", "3.0")
        def dep1 = node(2, "org", "dep1", "2.0", of(CONFLICT_RESOLUTION))
        root.outgoingEdges >> [
                dep(selector1, 1, 2),
                dep(selector2, 1, new RuntimeException("Boo!"))
        ]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(dep1)
        builder.visitSelector(selector1)
        builder.visitSelector(selector2)
        builder.visitEdges(root)

        builder.finish(root)

        when:
        def result = builder.complete([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:2.0(C) [root]
  org:dep2:3.0 -> org:dep2:3.0 - Could not resolve org:dep2:3.0.
"""
    }

    def "visiting resolved module version again has no effect"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector = selector(7, "org", "dep1", "2.0")
        root.outgoingEdges >> [dep(selector, 1, 2)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(1, "org", "root", "1.0", requested())) //it's fine

        builder.visitNode(node(2, "org", "dep1", "2.0", of(CONFLICT_RESOLUTION)))
        builder.visitNode(node(2, "org", "dep1", "2.0", requested())) //will be ignored

        builder.visitSelector(selector)

        builder.visitEdges(root)

        builder.finish(root)

        when:
        def result = builder.complete([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:2.0(C) [root]
"""
    }

    def "accumulates dependencies for all configurations of same component"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector1 = selector(10, "org", "dep1", "1.0")
        root.outgoingEdges >> [dep(selector1, 1, 2)]

        def conf1 = node(2, "org", "dep1", "1.0")
        def selector2 = selector(11, "org", "dep2", "1.0")
        conf1.outgoingEdges >> [dep(selector2, 2, 3)]

        def conf2 = node(2, "org", "dep1", "1.0")
        def selector3 = selector(12, "org", "dep3", "1.0")
        conf2.outgoingEdges >> [dep(selector3, 2, 4)]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(conf1)
        builder.visitNode(conf2)
        builder.visitNode(node(3, "org", "dep2", "1.0"))
        builder.visitNode(node(4, "org", "dep3", "1.0"))

        builder.visitSelector(selector1)
        builder.visitSelector(selector2)
        builder.visitSelector(selector3)

        builder.visitEdges(root)
        builder.visitEdges(conf1)
        builder.visitEdges(conf2)

        builder.finish(root)

        when:
        def result = builder.complete([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:1.0 [root]
    org:dep2:1.0 [dep1]
    org:dep3:1.0 [dep1]
"""
    }

    def "dependency failures are remembered"() {
        def root = rootNode(1, "org", "root", "1.0")
        def selector1 = selector(10, "org", "dep1", "1.0")
        def selector2 = selector(11, "org", "dep2", "2.0")
        root.outgoingEdges >> [
            dep(selector1, 1, new RuntimeException()),
            dep(selector2, 1, 3)
        ]
        def dep2 = node(3, "org", "dep2", "2.0")
        def selector3 = selector(12, "org", "dep1", "5.0")
        dep2.outgoingEdges >> [dep(selector3, 3, new RuntimeException())]

        builder.start(root)

        builder.visitNode(root)
        builder.visitNode(node(2, "org", "dep1", "2.0"))

        builder.visitNode(dep2)

        builder.visitSelector(selector1)
        builder.visitSelector(selector2)
        builder.visitSelector(selector3)

        builder.visitEdges(root)
        builder.visitEdges(dep2)

        builder.finish(root)

        when:
        def result = builder.complete([] as Set)

        then:
        printGraph(result.rootSource.get()) == """org:root:1.0
  org:dep1:1.0 -> org:dep1:1.0 - Could not resolve org:dep1:1.0.
  org:dep2:2.0 [root]
    org:dep1:5.0 -> org:dep1:5.0 - Could not resolve org:dep1:5.0.
"""
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, Long fromVariant, Long selectedId) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.selector >> selector
        _ * edge.selectedComponentId >> selectedId
        _ * edge.failure >> null
        _ * edge.fromVariant >> fromVariant
        _ * edge.selectedVariant >> selectedId
        return edge
    }

    private DependencyGraphEdge dep(DependencyGraphSelector selector, Long fromVariant, Throwable failure) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.selector >> selector
        _ * edge.requested >> selector.requested
        _ * edge.reason >> requested()
        _ * edge.failure >> new ModuleVersionResolveException(selector.requested, failure)
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
        _ * node.metadata >> Mock(LocalConfigurationGraphResolveMetadata) {
            getAttributes() >> AttributeTestUtil.attributes(["org.foo": "v1", "org.bar": 2, "org.baz": true])
        }
        return node
    }

    private DependencyGraphSelector selector(Long resultId, String org, String name, String ver) {
        def selector = Stub(DependencyGraphSelector)
        selector.resultId >> resultId
        selector.requested >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(org, name), new DefaultMutableVersionConstraint(ver))
        return selector
    }
}
