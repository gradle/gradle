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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.RepositoryChainModuleSource;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphComponent;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.VersionConflictResolutionDetails;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.artifacts.result.DefaultResolvedVariantResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.internal.Describables;
import org.gradle.internal.Pair;
import org.gradle.internal.component.external.model.DefaultImmutableCapability;
import org.gradle.internal.component.model.ComponentGraphResolveMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;

/**
 * Resolution state for a given component
 */
public class ComponentState implements ComponentResolutionState, DependencyGraphComponent {
    private final ComponentIdentifier componentIdentifier;
    private final ModuleVersionIdentifier id;
    private final ComponentMetaDataResolver resolver;
    private final AttributeDesugaring attributeDesugaring;
    private final List<NodeState> nodes = Lists.newLinkedList();
    private final Long resultId;
    private final ModuleResolveState module;
    private final List<ComponentSelectionDescriptorInternal> selectionCauses = Lists.newArrayList();
    private final DefaultImmutableCapability implicitCapability;
    private final int hashCode;

    private volatile ComponentGraphResolveState resolveState;

    private ComponentSelectionState state = ComponentSelectionState.Selectable;
    private ModuleVersionResolveException metadataResolveFailure;
    private ModuleSelectors<SelectorState> selectors;
    private DependencyGraphBuilder.VisitState visitState = DependencyGraphBuilder.VisitState.NotSeen;

    private boolean rejected;
    private boolean root;
    private Pair<Capability, Collection<NodeState>> capabilityReject;

    ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentIdentifier componentIdentifier, ComponentMetaDataResolver resolver, AttributeDesugaring attributeDesugaring) {
        this.resultId = resultId;
        this.module = module;
        this.id = id;
        this.componentIdentifier = componentIdentifier;
        this.resolver = resolver;
        this.implicitCapability = DefaultImmutableCapability.defaultCapabilityForComponent(id);
        this.attributeDesugaring = attributeDesugaring;
        this.hashCode = 31 * id.hashCode() ^ resultId.hashCode();
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
    public String getRepositoryId() {
        return resolveState.getSources().withSource(RepositoryChainModuleSource.class, source -> source
            .map(RepositoryChainModuleSource::getRepositoryId)
            .orElse(null));
    }

    @Override
    public ModuleVersionIdentifier getModuleVersion() {
        return id;
    }

    @Nullable
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

    ModuleResolveState getModule() {
        return module;
    }

    public void selectAndRestartModule() {
        module.replaceWith(this);
    }

    @Override
    @Nullable
    public ComponentGraphResolveMetadata getMetadataOrNull() {
        resolve();
        if (resolveState == null) {
            return null;
        } else {
            return resolveState.getMetadata();
        }
    }

    public ComponentGraphResolveMetadata getMetadata() {
        resolve();
        return resolveState.getMetadata();
    }

    @Override
    public ComponentGraphResolveState getResolveState() {
        resolve();
        assert resolveState != null;
        return resolveState;
    }

    @Nullable
    public ComponentGraphResolveState getResolveStateOrNull() {
        resolve();
        return resolveState;
    }

    @Override
    public ComponentIdentifier getComponentId() {
        // Use the resolved component id if available: this ensures that Maven Snapshot ids are correctly reported
        if (resolveState != null) {
            return resolveState.getId();
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

    public void setSelectors(ModuleSelectors<SelectorState> selectors) {
        this.selectors = selectors;
    }

    /**
     * Returns true if this module version can be resolved quickly (already resolved or local)
     *
     * @return true if it has been resolved in a cheap way
     */
    public boolean alreadyResolved() {
        return resolveState != null || metadataResolveFailure != null;
    }

    public void resolve() {
        if (alreadyResolved()) {
            return;
        }

        ComponentOverrideMetadata componentOverrideMetadata;
        if (selectors != null && selectors.size() > 0) {
            // Taking the first selector here to determine the 'changing' status and 'client module' is our best bet to get the selector that will most likely be chosen in the end.
            // As selectors are sorted accordingly (see ModuleSelectors.SELECTOR_COMPARATOR).
            SelectorState firstSelector = selectors.first();
            componentOverrideMetadata = DefaultComponentOverrideMetadata.forDependency(firstSelector.isChanging(), selectors.getFirstDependencyArtifact(), firstSelector.getClientModule());
        } else {
            componentOverrideMetadata = DefaultComponentOverrideMetadata.EMPTY;
        }
        if (tryResolveVirtualPlatform()) {
            return;
        }
        DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
        resolver.resolve(componentIdentifier, componentOverrideMetadata, result);

        if (result.getFailure() != null) {
            metadataResolveFailure = result.getFailure();
            return;
        }
        resolveState = result.getState();
    }

    private boolean tryResolveVirtualPlatform() {
        if (module.isVirtualPlatform()) {
            for (ComponentState version : module.getAllVersions()) {
                if (version != this) {
                    ComponentGraphResolveState versionState = version.getResolveStateOrNull();
                    if (versionState != null) {
                        ComponentGraphResolveState lenient = versionState.maybeAsLenientPlatform((ModuleComponentIdentifier) componentIdentifier, id);
                        if (lenient != null) {
                            setState(lenient);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public void setState(ComponentGraphResolveState state) {
        this.resolveState = state;
        this.metadataResolveFailure = null;
    }

    public void addConfiguration(NodeState node) {
        nodes.add(node);
    }

    private ComponentSelectionReason cachedReason;

    @Override
    public ComponentSelectionReason getSelectionReason() {
        if (root) {
            return ComponentSelectionReasons.root();
        }
        if (cachedReason != null) {
            return cachedReason;
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
        cachedReason = reason;
        return reason;
    }

    boolean hasStrongOpinion() {
        return StreamSupport.stream(module.getSelectors().spliterator(), false)
            .filter(s -> s.getFailure() == null)
            .anyMatch(SelectorState::hasStrongOpinion);
    }

    @Override
    public void addCause(ComponentSelectionDescriptorInternal componentSelectionDescriptor) {
        selectionCauses.add(componentSelectionDescriptor);
    }

    public void setRoot() {
        this.root = true;
    }

    @Override
    public List<ResolvedVariantResult> getResolvedVariants() {
        ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builder();
        addResolvedVariants(builder::add);
        return builder.build();
    }

    @Override
    public List<ResolvedVariantResult> getAllVariants() {
        // Without mixing in resolved variants here, we don't return all variants selected in the case of project dependencies.
        // Additionally, we wouldn't have the external variants that we get from getResolvedVariants().
        // TODO: Figure out why the variants from getVariantsForGraphTraversal() are different in the case of project dependencies.
        Set<String> seen = new HashSet<>();
        ImmutableList.Builder<ResolvedVariantResult> builder = ImmutableList.builder();
        Consumer<ResolvedVariantResult> resolvedVariantResultConsumer = v -> {
            if (seen.add(v.getDisplayName())) {
                builder.add(v);
            }
        };
        addResolvedVariants(resolvedVariantResultConsumer);
        addOtherVariants(resolvedVariantResultConsumer);
        return builder.build();
    }

    private void addResolvedVariants(Consumer<ResolvedVariantResult> consumer) {
        for (NodeState node : nodes) {
            if (node.isSelected()) {
                consumer.accept(node.getResolvedVariant());
            }
        }
    }

    private void addOtherVariants(Consumer<ResolvedVariantResult> consumer) {
        Optional<? extends List<? extends VariantGraphResolveMetadata>> variants = resolveState.getMetadata().getVariantsForGraphTraversal();
        if (variants.isPresent()) {
            for (VariantGraphResolveMetadata mainVariant : variants.get()) {
                for (VariantGraphResolveMetadata.Subvariant variant : mainVariant.getVariants()) {
                    List<? extends Capability> capabilities = variant.getCapabilities().getCapabilities();
                    if (capabilities.isEmpty()) {
                        capabilities = ImmutableList.of(getImplicitCapability());
                    } else {
                        capabilities = ImmutableList.copyOf(capabilities);
                    }
                    consumer.accept(new DefaultResolvedVariantResult(
                        resolveState.getId(),
                        Describables.of(variant.getName()),
                        attributeDesugaring.desugar(variant.getAttributes().asImmutable()),
                        capabilities,
                        null
                    ));
                }
            }
        }
        // Fall-back if there's no graph data
        for (NodeState node : nodes) {
            for (VariantGraphResolveMetadata.Subvariant variant : node.getMetadata().getVariants()) {
                consumer.accept(new DefaultResolvedVariantResult(
                    resolveState.getId(),
                    Describables.of(variant.getName()),
                    node.desugar(variant.getAttributes().asImmutable()),
                    ImmutableList.of(),
                    null
                ));
            }
        }
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

    public void rejectForCapabilityConflict(Capability capability, Collection<NodeState> conflictedNodes) {
        this.rejected = true;
        if (this.capabilityReject == null) {
            this.capabilityReject = Pair.of(capability, conflictedNodes);
        } else {
            mergeCapabilityRejects(capability, conflictedNodes);
        }
    }

    private void mergeCapabilityRejects(Capability capability, Collection<NodeState> conflictedNodes) {
        // Only merge if about the same capability, otherwise last wins
        if (this.capabilityReject.getLeft().equals(capability)) {
            this.capabilityReject.getRight().addAll(conflictedNodes);
        } else {
            this.capabilityReject = Pair.of(capability, conflictedNodes);
        }
    }

    @Override
    public boolean isRejected() {
        return rejected;
    }

    public String getRejectedErrorMessage() {
        if (capabilityReject != null) {
            return formatCapabilityRejectMessage(module.getId(), capabilityReject);
        } else {
            return new RejectedModuleMessageBuilder().buildFailureMessage(module);
        }

    }

    private String formatCapabilityRejectMessage(ModuleIdentifier id, Pair<Capability, Collection<NodeState>> capabilityConflict) {
        StringBuilder sb = new StringBuilder("Module '");
        sb.append(id).append("' has been rejected:\n");
        sb.append("   Cannot select module with conflict on ");
        Capability capability = capabilityConflict.left;
        sb.append("capability '").append(capability.getGroup()).append(":").append(capability.getName()).append(":").append(capability.getVersion()).append("' also provided by ");
        sb.append(capabilityConflict.getRight());
        return sb.toString();
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

    Capability getImplicitCapability() {
        return implicitCapability;
    }

    @Nullable
    Capability findCapability(String group, String name) {
        if (id.getGroup().equals(group) && id.getName().equals(name)) {
            return implicitCapability;
        }
        return null;
    }

    boolean hasMoreThanOneSelectedNodeUsingVariantAwareResolution() {
        int count = 0;
        for (NodeState node : nodes) {
            if (node.isSelectedByVariantAwareResolution()) {
                count++;
                if (count == 2) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ComponentState that = (ComponentState) o;

        return that.resultId.equals(resultId);

    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
