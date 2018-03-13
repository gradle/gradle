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
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.CapabilityDescriptor;
import org.gradle.api.internal.artifacts.ResolvedVersionConstraint;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.ImmutableCapability;
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
public class ComponentState implements ComponentResolutionState, DependencyGraphComponent, ComponentStateWithDependents<ComponentState> {
    private final ModuleVersionIdentifier id;
    private final ComponentMetaDataResolver resolver;
    private final VariantNameBuilder variantNameBuilder;
    private final List<NodeState> nodes = Lists.newLinkedList();
    private final Long resultId;
    private final ModuleResolveState module;
    private final ComponentSelectionReasonInternal selectionReason = VersionSelectionReasons.empty();
    private final ImmutableCapability implicitCapability;
    private volatile ComponentResolveMetadata metaData;

    private ComponentSelectionState state = ComponentSelectionState.Selectable;
    private ModuleVersionResolveException failure;
    private SelectorState selectedBy;
    private DependencyGraphBuilder.VisitState visitState = DependencyGraphBuilder.VisitState.NotSeen;
    List<SelectorState> allResolvers;

    ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentMetaDataResolver resolver, VariantNameBuilder variantNameBuilder) {
        this.resultId = resultId;
        this.module = module;
        this.id = id;
        this.resolver = resolver;
        this.variantNameBuilder = variantNameBuilder;
        this.implicitCapability = new ImmutableCapability(id.getGroup(), id.getName(), id.getVersion());
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
        String name = null;
        for (NodeState node : nodes) {
            if (node.isSelected()) {
                name = variantNameBuilder.getVariantName(name, node.getMetadata().getName());
            }
        }
        return name == null ? "unknown" : name;
    }

    @Override
    public AttributeContainer getVariantAttributes() {
        NodeState selected = getSelectedNode();
        return selected == null ? ImmutableAttributes.EMPTY : desugarAttributes(selected);
    }

    /**
     * Returns the _first_ selected node. There may be multiple.
     */
    private NodeState getSelectedNode() {
        for (NodeState node : nodes) {
            if (node.isSelected()) {
                return node;
            }
        }
        return null;
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

    public boolean isSelected() {
        return state == ComponentSelectionState.Selected;
    }

    boolean isSelectable() {
        return state.isSelectable();
    }

    boolean isCandidateForConflictResolution() {
        return state.isCandidateForConflictResolution();
    }

    void evict() {
        state = ComponentSelectionState.Evicted;
    }

    void select() {
        state = ComponentSelectionState.Selected;
    }

    void makeSelectable() {
        state = ComponentSelectionState.Selectable;
    }

    /**
     * Describes the possible states of a component in the graph.
     */
    enum ComponentSelectionState {
        /**
         * A selectable component is either new to the graph, or has been visited before,
         * but wasn't selected because another compatible version was used.
         */
        Selectable(true, true),

        /**
         * A selected component has been chosen, at some point, as the version to use.
         * This is not for a lifetime: a component can later be evicted through conflict resolution,
         * or another compatible component can be chosen instead if more constraints arise.
         */
        Selected(true, false),

        /**
         * An evicted component has been evicted and will never, ever be chosen starting from the moment it is evicted.
         * Either because it has been excluded, or because conflict resolution selected a different version.
         */
        Evicted(false, false);

        private final boolean candidateForConflictResolution;
        private final boolean canSelect;

        ComponentSelectionState(boolean candidateForConflictResolution, boolean canSelect) {
            this.candidateForConflictResolution = candidateForConflictResolution;
            this.canSelect = canSelect;
        }

        boolean isCandidateForConflictResolution() {
            return candidateForConflictResolution;
        }

        public boolean isSelectable() {
            return canSelect;
        }
    }


    public void forEachCapability(Action<? super CapabilityDescriptor> action) {
        // first, implicitly add a capability corresponding to the module
        action.execute(implicitCapability);

        // check conflict for each target node
        for (NodeState target : nodes) {
            List<? extends CapabilityDescriptor> capabilities = target.getMetadata().getCapabilitiesMetadata().getCapabilities();
            // The isEmpty check is not required, might look innocent, but Guava's performance bad for an empty immutable list
            // because it still creates an inner class for an iterator, which delegates to an Array iterator, which does... nothing.
            // so just adding this check has a significant impact because most components do not declare any capability
            if (!capabilities.isEmpty()) {
                for (CapabilityDescriptor capability : capabilities) {
                    action.execute(capability);
                }
            }
        }
    }

    public CapabilityDescriptor findCapability(String group, String name) {
        if (id.getGroup().equals(group) && id.getName().equals(name)) {
            return implicitCapability;
        }
        return findCapabilityOnTarget(group, name);
    }

    private CapabilityDescriptor findCapabilityOnTarget(String group, String name) {
        for (NodeState target : nodes) {
            List<? extends CapabilityDescriptor> capabilities = target.getMetadata().getCapabilitiesMetadata().getCapabilities();
            if (!capabilities.isEmpty()) { // Not required, but Guava's performance bad for an empty immutable list
                for (CapabilityDescriptor capability : capabilities) {
                    if (capability.getGroup().equals(group) && capability.getName().equals(name)) {
                        return capability;
                    }
                }
            }
        }
        return null;
    }

}
