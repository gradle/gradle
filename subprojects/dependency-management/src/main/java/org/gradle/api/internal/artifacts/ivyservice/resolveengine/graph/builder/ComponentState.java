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
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChainModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.selectors.ResolvableSelectorState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Resolution state for a given component
 */
public class ComponentState implements ComponentResolutionState, DependencyGraphComponent {
    private static final DisplayName UNKNOWN_VARIANT = Describables.of("unknown");

    private final ComponentIdentifier componentIdentifier;
    private final ModuleVersionIdentifier id;
    private final ComponentMetaDataResolver resolver;
    private final VariantNameBuilder variantNameBuilder;
    private final List<NodeState> nodes = Lists.newLinkedList();
    private final Long resultId;
    private final ModuleResolveState module;
    private final List<ComponentSelectionDescriptorInternal> selectionCauses = Lists.newArrayList();
    private final ImmutableCapability implicitCapability;

    private volatile ComponentResolveMetadata metadata;

    private ComponentSelectionState state = ComponentSelectionState.Selectable;
    private ModuleVersionResolveException metadataResolveFailure;
    private SelectorState firstSelectedBy;
    private DependencyGraphBuilder.VisitState visitState = DependencyGraphBuilder.VisitState.NotSeen;

    private boolean rejected;
    private boolean root;

    ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, ComponentMetaDataResolver resolver, VariantNameBuilder variantNameBuilder) {
        this.resultId = resultId;
        this.module = module;
        this.id = id;
        this.componentIdentifier = componentIdentifier;
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
    public String getRepositoryName() {
        ModuleSource moduleSource = metadata.getSource();
        if (moduleSource instanceof RepositoryChainModuleSource) {
            return ((RepositoryChainModuleSource) moduleSource).getRepositoryName();
        }
        return null;
    }

    @Override
    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    public ModuleVersionResolveException getMetadataResolveFailure() {
        return metadataResolveFailure;
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

    public ModuleResolveState getModule() {
        return module;
    }

    @Override
    public ComponentResolveMetadata getMetadata() {
        resolve();
        return metadata;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        // Use the resolved component id if available: this ensures that Maven Snapshot ids are correctly reported
        if (metadata != null) {
            return metadata.getId();
        }
        return componentIdentifier;
    }

    /**
     * Restarts all incoming edges for this component, queuing them up for processing.
     */
    public void restartIncomingEdges(ComponentState selected) {
        for (NodeState configuration : nodes) {
            configuration.restart(selected);
        }
    }

    @Override
    public void selectedBy(ResolvableSelectorState selectorState) {
        if (firstSelectedBy == null) {
            firstSelectedBy = (SelectorState) selectorState;
        }
    }

    /**
     * Returns true if this module version can be resolved quickly (already resolved or local)
     *
     * @return true if it has been resolved in a cheap way
     */
    public boolean alreadyResolved() {
        return metadata != null || metadataResolveFailure != null;
    }

    public void resolve() {
        if (alreadyResolved()) {
            return;
        }

        // Any metadata overrides (e.g classifier/artifacts/client-module) will be taken from the first dependency that referenced this component
        ComponentOverrideMetadata componentOverrideMetadata = DefaultComponentOverrideMetadata.forDependency(firstSelectedBy.getDependencyMetadata());

        DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
        if (tryResolveVirtualPlatform()) {
            return;
        }
        resolver.resolve(componentIdentifier, componentOverrideMetadata, result);

        if (result.getFailure() != null) {
            metadataResolveFailure = result.getFailure();
            return;
        }
        metadata = result.getMetadata();
    }

    private boolean tryResolveVirtualPlatform() {
        if (module.isVirtualPlatform()) {
            for (ComponentState version : module.getAllVersions()) {
                if (version != this) {
                    ComponentResolveMetadata metadata = version.getMetadata();
                    if (metadata instanceof LenientPlatformResolveMetadata) {
                        LenientPlatformResolveMetadata lenient = (LenientPlatformResolveMetadata) metadata;
                        this.metadata = lenient.withVersion((ModuleComponentIdentifier) componentIdentifier, id);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void setMetadata(ComponentResolveMetadata metaData) {
        this.metadata = metaData;
        this.metadataResolveFailure = null;
    }

    public void addConfiguration(NodeState node) {
        nodes.add(node);
    }

    @Override
    public ComponentSelectionReason getSelectionReason() {
        if (root) {
            return ComponentSelectionReasons.root();
        }
        ComponentSelectionReasonInternal reason = ComponentSelectionReasons.empty();
        for (final SelectorState selectorState : module.getSelectors()) {
            if (selectorState.getFailure() == null) {
                selectorState.addReasonsForSelector(reason);
            }
        }
        for (ComponentSelectionDescriptorInternal selectionCause : VersionConflictResolutionDetails.mergeCauses(selectionCauses)) {
            reason.addCause(selectionCause);
        }
        return reason;
    }

    @Override
    public void addCause(ComponentSelectionDescriptorInternal componentSelectionDescriptor) {
        selectionCauses.add(componentSelectionDescriptor);
    }

    public void setRoot() {
        this.root = true;
    }

    @Override
    public DisplayName getVariantName() {
        DisplayName name = null;
        List<String> names = null;
        for (NodeState node : nodes) {
            if (node.isSelected()) {
                if (names == null) {
                    names = Lists.newArrayListWithCapacity(nodes.size());
                }
                names.add(node.getMetadata().getName());
            }
        }
        name = variantNameBuilder.getVariantName(names);
        return name == null ? UNKNOWN_VARIANT : name;
    }

    @Override
    public AttributeContainer getVariantAttributes() {
        NodeState selected = getSelectedNode();
        return selected == null ? ImmutableAttributes.EMPTY : AttributeDesugaring.desugar(selected.getMetadata().getAttributes(), selected.getAttributesFactory());
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

    @Override
    public Collection<? extends ModuleVersionIdentifier> getAllVersions() {
        Collection<ComponentState> moduleVersions = module.getAllVersions();
        List<ModuleVersionIdentifier> out = Lists.newArrayListWithCapacity(moduleVersions.size());
        for (ComponentState moduleVersion : moduleVersions) {
            out.add(moduleVersion.id);
        }
        return out;
    }

    public boolean isSelected() {
        return state == ComponentSelectionState.Selected;
    }

    public boolean isCandidateForConflictResolution() {
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

    @Override
    public void reject() {
        this.rejected = true;

    }

    @Override
    public boolean isRejected() {
        return rejected;
    }

    @Override
    public Set<VirtualPlatformState> getPlatformOwners() {
        return module.getPlatformOwners();
    }

    @Override
    public VirtualPlatformState getPlatformState() {
        return module.getPlatformState();
    }

    public void removeOutgoingEdges() {
        for (NodeState configuration : getNodes()) {
            configuration.deselect();
        }
    }

    /**
     * Describes the possible states of a component in the graph.
     */
    enum ComponentSelectionState {
        /**
         * A selectable component is either new to the graph, or has been visited before,
         * but wasn't selected because another compatible version was used.
         */
        Selectable(true),

        /**
         * A selected component has been chosen, at some point, as the version to use.
         * This is not for a lifetime: a component can later be evicted through conflict resolution,
         * or another compatible component can be chosen instead if more constraints arise.
         */
        Selected(true),

        /**
         * An evicted component has been evicted and will never, ever be chosen starting from the moment it is evicted.
         * Either because it has been excluded, or because conflict resolution selected a different version.
         */
        Evicted(false);

        private final boolean candidateForConflictResolution;

        ComponentSelectionState(boolean candidateForConflictResolution) {
            this.candidateForConflictResolution = candidateForConflictResolution;
        }

        boolean isCandidateForConflictResolution() {
            return candidateForConflictResolution;
        }
    }


    public void forEachCapability(Action<? super Capability> action) {
        // check conflict for each target node
        for (NodeState target : nodes) {
            List<? extends Capability> capabilities = target.getMetadata().getCapabilities().getCapabilities();
            // The isEmpty check is not required, might look innocent, but Guava's performance bad for an empty immutable list
            // because it still creates an inner class for an iterator, which delegates to an Array iterator, which does... nothing.
            // so just adding this check has a significant impact because most components do not declare any capability
            if (!capabilities.isEmpty()) {
                for (Capability capability : capabilities) {
                    action.execute(capability);
                }
            }
        }
    }

    public Capability findCapability(String group, String name) {
        if (id.getGroup().equals(group) && id.getName().equals(name)) {
            return implicitCapability;
        }
        return findCapabilityOnTarget(group, name);
    }

    private Capability findCapabilityOnTarget(String group, String name) {
        for (NodeState target : nodes) {
            List<? extends Capability> capabilities = target.getMetadata().getCapabilities().getCapabilities();
            if (!capabilities.isEmpty()) { // Not required, but Guava's performance bad for an empty immutable list
                for (Capability capability : capabilities) {
                    if (capability.getGroup().equals(group) && capability.getName().equals(name)) {
                        return capability;
                    }
                }
            }
        }
        return null;
    }
}
