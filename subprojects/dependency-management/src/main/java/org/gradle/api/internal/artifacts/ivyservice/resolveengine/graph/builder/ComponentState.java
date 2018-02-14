/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.builder;

import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ComponentResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import java.util.List;
import java.util.Set;

/**
 * Resolution state for a given component
 */
public class ComponentState implements ComponentResolutionState, ComponentResult, DependencyGraphComponent, ComponentStateWithDependents<ComponentState> {
    private final ModuleVersionIdentifier id;
    private final ComponentMetaDataResolver resolver;
    private final List<NodeState> nodes = Lists.newLinkedList();
    private final Long resultId;
    private final ModuleResolveState module;
    private final ComponentSelectionReasonInternal selectionReason = VersionSelectionReasons.empty();
    private volatile ComponentResolveMetadata metaData;

    private ModuleState state = ModuleState.Selectable;
    private ModuleVersionResolveException failure;
    private SelectorState selectedBy;
    private DependencyGraphBuilder.VisitState visitState = DependencyGraphBuilder.VisitState.NotSeen;
    List<SelectorState> allResolvers;

    ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentMetaDataResolver resolver) {
        this.resultId = resultId;
        this.module = module;
        this.id = id;
        this.resolver = resolver;
    }

    @Override
    public String toString() {
        return id.toString();
    }

    @Override
    public String getVersion() {
        return id.getVersion();
    }

    @Override
    public Long getResultId() {
        return resultId;
    }

    @Override
    public ModuleVersionIdentifier getId() {
        return id;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    public ModuleVersionResolveException getFailure() {
        return failure;
    }

    public DependencyGraphBuilder.VisitState getVisitState() {
        return visitState;
    }

    public void setVisitState(DependencyGraphBuilder.VisitState visitState) {
        this.visitState = visitState;
    }

    public List<NodeState> getNodes() {
        return nodes;
    }

    ModuleResolveState getModule() {
        return module;
    }

    @Override
    public ComponentResolveMetadata getMetadata() {
        return metaData;
    }

    public void restart(ComponentState selected) {
        for (NodeState configuration : nodes) {
            configuration.restart(selected);
        }
    }

    public void selectedBy(SelectorState resolver) {
        if (selectedBy == null) {
            selectedBy = resolver;
            allResolvers = Lists.newLinkedList();
        }
        allResolvers.add(resolver);
    }

    /**
     * Returns true if this module version can be resolved quickly (already resolved or local)
     *
     * @return true if it has been resolved in a cheap way
     */
    public boolean fastResolve() {
        if (metaData != null || failure != null) {
            return true;
        }

        ComponentIdResolveResult idResolveResult = selectedBy.getResolveResult();
        if (idResolveResult.getFailure() != null) {
            failure = idResolveResult.getFailure();
            return true;
        }
        if (idResolveResult.getMetaData() != null) {
            metaData = idResolveResult.getMetaData();
            return true;
        }
        return false;
    }

    public void resolve() {
        if (fastResolve()) {
            return;
        }

        ComponentIdResolveResult idResolveResult = selectedBy.getResolveResult();

        DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
        resolver.resolve(idResolveResult.getId(), DefaultComponentOverrideMetadata.forDependency(selectedBy.getDependencyMetadata()), result);
        if (result.getFailure() != null) {
            failure = result.getFailure();
            return;
        }
        metaData = result.getMetaData();
    }

    @Override
    public ComponentResolveMetadata getMetaData() {
        if (metaData == null) {
            resolve();
        }
        return metaData;
    }

    public ResolvedVersionConstraint getVersionConstraint() {
        return selectedBy == null ? null : selectedBy.getVersionConstraint();
    }

    @Override
    public boolean isResolved() {
        return metaData != null;
    }

    public void setMetaData(ComponentResolveMetadata metaData) {
        this.metaData = metaData;
        this.failure = null;
    }

    public void addConfiguration(NodeState node) {
        nodes.add(node);
    }

    @Override
    public ComponentSelectionReasonInternal getSelectionReason() {
        return selectionReason;
    }

    @Override
    public void addCause(ComponentSelectionDescriptorInternal reason) {
        selectionReason.addCause(reason);
    }


    public void setRoot() {
        selectionReason.setCause(VersionSelectionReasons.ROOT);
    }

    @Override
    public ComponentIdentifier getComponentId() {
        return getMetaData().getComponentId();
    }

    @Override
    public String getVariantName() {
        NodeState selected = getSelectedNode();
        return selected == null ? "unknown" : selected.getMetadata().getName();
    }

    private NodeState getSelectedNode() {
        for (NodeState node : nodes) {
            if (node.isSelected()) {
                return node;
            }
        }
        return null;
    }

    @Override
    public AttributeContainer getVariantAttributes() {
        NodeState selected = getSelectedNode();
        return selected == null ? ImmutableAttributes.EMPTY : desugarAttributes(selected);
    }

    /**
     * Desugars attributes so that what we're going to serialize consists only of String or Boolean attributes,
     * and not their original types.
     * @param selected the selected component
     * @return desugared attributes
     */
    private ImmutableAttributes desugarAttributes(NodeState selected) {
        ImmutableAttributes attributes = selected.getMetadata().getAttributes();
        if (attributes.isEmpty()) {
            return attributes;
        }
        AttributeContainerInternal mutable = selected.getAttributesFactory().mutable();
        Set<Attribute<?>> keySet = attributes.keySet();
        for (Attribute<?> attribute : keySet) {
            Object value = attributes.getAttribute(attribute);
            Attribute<Object> desugared = Cast.uncheckedCast(attribute);
            if (attribute.getType() == Boolean.class || attribute.getType() == String.class) {
                mutable.attribute(desugared, value);
            } else {
                desugared = Cast.uncheckedCast(Attribute.of(attribute.getName(), String.class));
                mutable.attribute(desugared, value.toString());
            }
        }
        return mutable.asImmutable();
    }

    @Override
    public List<ComponentState> getDependents() {
        List<ComponentState> incoming = Lists.newArrayListWithCapacity(nodes.size());
        for (NodeState configuration : nodes) {
            for (EdgeState dependencyEdge : configuration.getIncomingEdges()) {
                incoming.add(dependencyEdge.getFrom().getComponent());
            }
        }
        return incoming;
    }

    public List<ComponentState> getUnattachedDependencies() {
        return module.getUnattachedEdgesTo(this);
    }

    @Override
    public boolean isFromPendingNode() {
        return selectedBy != null && selectedBy.getDependencyMetadata().isPending();
    }

    boolean isSelected() {
        return state == ModuleState.Selected;
    }

    boolean isSelectable() {
        return state.isSelectable();
    }

    boolean isCandidateForConflictResolution() {
        return state.isCandidateForConflictResolution();
    }

    void evict() {
        state = ModuleState.Evicted;
    }

    void select() {
        state = ModuleState.Selected;
    }

    void makeSelectable() {
        state = ModuleState.Selectable;
    }

}
