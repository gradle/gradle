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

/**
 * Shared fixtures for building a resolved dependency graph via {@link StreamingResolutionResultBuilder}
 * and stubbing the graph nodes/edges it visits.
 */
abstract class AbstractResolutionResultBuilderTest extends Specification {

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

    protected DependencyGraphComponent component(String org, String name, String ver, ComponentSelectionReason reason = ComponentSelectionReasons.requested()) {
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

    protected DependencyGraphEdge dep(DependencyGraphNode node) {
        def moduleVersionId = node.owner.resolveState.metadata.moduleVersionId
        def selector = selector(moduleVersionId.group, moduleVersionId.name, moduleVersionId.version)
        return dep(selector, node)
    }

    protected DependencyGraphEdge dep(DependencyGraphSelector selector, DependencyGraphNode selected) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.requested >> selector.requested
        _ * edge.selector >> selector
        _ * edge.failure >> null
        _ * edge.targetNodes >> [selected]
        return edge
    }

    protected DependencyGraphEdge dep(DependencyGraphSelector selector, Throwable failure) {
        def edge = Stub(DependencyGraphEdge)
        _ * edge.selector >> selector
        _ * edge.requested >> selector.requested
        _ * edge.reason >> ComponentSelectionReasons.requested()
        _ * edge.failure >> new ModuleVersionResolveException(selector.requested, failure)
        return edge
    }

    protected DependencyGraphNode node(DependencyGraphComponent component) {
        int nodeId = nodeIds++
        def node = Stub(DependencyGraphNode) {
            getOwner() >> component
            getNodeId() >> nodeId
            getExternalVariant() >> null
        }
        component.selectedVariants.add(node)
        return node
    }

    protected RootGraphNode rootNode(String org, String name, String ver) {
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

    protected DependencyGraphSelector selector(String org, String name, String ver) {
        def selector = Stub(DependencyGraphSelector)
        selector.requested >> DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(org, name), new DefaultMutableVersionConstraint(ver))
        return selector
    }
}
