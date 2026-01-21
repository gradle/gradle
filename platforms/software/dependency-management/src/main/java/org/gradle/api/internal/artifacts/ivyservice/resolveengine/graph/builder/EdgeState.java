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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.component.ComponentSelectorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.immutable.ImmutableAttributesSchema;
import org.gradle.internal.Describables;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelector;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.BY_ANCESTOR;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.CONSTRAINT;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.FORCED;
import static org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons.REQUESTED;

/**
 * Represents the edges in the dependency graph.
 *
 * A dependency can have the following states:
 * 1. Unattached: in this case the state of the dependency is tied to the state of it's associated {@link SelectorState}.
 * 2. Attached: in this case the Edge has been connected to actual nodes in the target component. Only possible if the {@link SelectorState} did not fail to resolve.
 */
class EdgeState implements DependencyGraphEdge {
    private final DependencyState dependencyState;
    private final DependencyMetadata dependencyMetadata;
    private final NodeState from;
    private final ResolveState resolveState;
    private final List<NodeState> targetNodes = new LinkedList<>();
    private final boolean isTransitive;
    private final boolean isConstraint;

    private @Nullable SelectorState selector;
    private ModuleVersionResolveException targetNodeSelectionFailure;

    /**
     * The accumulated exclusions that apply to this edge based on the paths from the root
     */
    private @Nullable ExcludeSpec transitiveExclusions;
    private ExcludeSpec cachedEdgeExclusions;
    private ExcludeSpec cachedExclusions;

    private boolean unattached;

    public EdgeState(
        NodeState from,
        DependencyMetadata metadata,
        ComponentSelector requested,
        ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors,
        @Nullable ModuleVersionResolveException resolveFailure,
        ResolveState resolveState
    ) {
        this.from = from;
        this.dependencyMetadata = metadata;
        this.resolveState = resolveState;
        this.isTransitive = from.isTransitive() && dependencyMetadata.isTransitive();
        this.isConstraint = dependencyMetadata.isConstraint();

        // TODO: DependencyState should eventually be merged into EdgeState
        this.dependencyState = new DependencyState(metadata, requested, ruleDescriptors, resolveFailure);
    }

    boolean computeSelector(StrictVersionConstraints ancestorsStrictVersions, boolean deferSelection) {
        boolean ignoreVersion = !dependencyState.isForced() && ancestorsStrictVersions.contains(dependencyState.getModuleIdentifier(resolveState.getComponentSelectorConverter()));
        SelectorState newSelector = resolveState.computeSelectorFor(dependencyState, ignoreVersion);
        if (this.selector != newSelector) {
            clearSelector();
            newSelector.use(deferSelection);
            this.selector = newSelector;
            return true;
        }

        return false;
    }

    public void clearSelector() {
        if (this.selector != null) {
            this.selector.release();
            this.selector = null;
        }
    }

    @Override
    public String toString() {
        return String.format("%s -> %s", from.toString(), dependencyMetadata);
    }

    @Override
    public NodeState getFrom() {
        return from;
    }

    @Override
    public DependencyMetadata getDependencyMetadata() {
        return dependencyMetadata;
    }

    /**
     * Returns the target component, if the edge has been successfully resolved.
     * Returns null if the edge failed to resolve, or has not (yet) been successfully resolved to a target component.
     */
    @Nullable
    ComponentState getTargetComponent() {
        if (selector == null || !selector.isResolved() || selector.getFailure() != null) {
            return null;
        }
        return getSelectedComponent();
    }

    SelectorState getSelector() {
        assert selector != null : "No selector for " + this;
        return selector;
    }

    @Override
    public boolean isTransitive() {
        return isTransitive;
    }

    void attachToTargetNodes() {
        ComponentState targetComponent = getTargetComponent();
        if (targetComponent == null || !isUsed()) {
            // The selector failed or the module has been deselected or the edge source has been deselected. Do not attach.
            return;
        }

        // We should never try to attach edges to a node in a module that has no incoming hard edges.
        assert !targetComponent.getModule().isPending();

        calculateTargetNodes(targetComponent);
        if (!targetNodes.isEmpty()) {
            for (NodeState targetNode : targetNodes) {
                targetNode.addIncomingEdge(this);
            }
            selector.getTargetModule().removeUnattachedEdge(this);
        }
    }

    /**
     * Disconnect this edge from any node that it currently targets,
     * ensuring the target knows it is no longer being pointed to by
     * this edge.
     */
    void detachFromTargetNodes() {
        if (!targetNodes.isEmpty()) {
            for (NodeState targetNode : targetNodes) {
                targetNode.removeIncomingEdge(this);
            }
            targetNodes.clear();
        }
        targetNodeSelectionFailure = null;
    }

    /**
     * Call this method to attach a failure late in the process. This is typically
     * done when a failure is caused by graph validation. In that case we want to
     * perform as much resolution as possible, still have a valid graph, but in the
     * end fail resolution.
     */
    void failWith(Throwable err) {
        targetNodeSelectionFailure = new ModuleVersionResolveException(selector.getSelector(), err);
    }

    /**
     * Ensure this edge it up-to-date and attached to the proper nodes, effectively
     * retargeting this edge from its previous potentially incorrect target, to
     * the new correct target.
     * <p>
     * Useful for when the state of the destination has changed, for example
     * when the selected component of the target module has changed.
     */
    public void retarget() {
        detachFromTargetNodes();
        if (isUsed()) {
            attachToTargetNodes();
            if (targetNodes.isEmpty()) {
                selector.getTargetModule().addUnattachedEdge(this); // Attach failed, mark it as such.
            }
        }
    }

    @Override
    public ImmutableAttributes getAttributes() {
        ModuleResolveState module = selector.getTargetModule();
        ComponentSelectorInternal componentSelector = (ComponentSelectorInternal) dependencyMetadata.getSelector();
        return resolveState.getAttributesFactory().safeConcat(module.getMergedConstraintAttributes(), componentSelector.getAttributes());
    }

    private void calculateTargetNodes(ComponentState targetComponent) {
        ComponentGraphResolveState targetComponentState = targetComponent.getResolveStateOrNull();
        targetNodes.clear(); // TODO: Why not `detachFromTargetNodes()`?
        targetNodeSelectionFailure = null;
        if (targetComponentState == null) {
            targetComponent.getModule().getPlatformState().addOrphanEdge(this);
            // Broken version
            return;
        }
        if (isConstraint) {
            // We are a constraint and therefore may have deferred selection and attachment
            // of some other module/edge. Make sure to attach that deferred edge now that we have
            // performed selection.
            List<EdgeState> unattachedEdges = targetComponent.getModule().getUnattachedEdges();
            if (!unattachedEdges.isEmpty()) {
                for (EdgeState otherEdge : new ArrayList<>(unattachedEdges)) {
                    if (!otherEdge.isConstraint()) {
                        otherEdge.attachToTargetNodes();
                        if (otherEdge.targetNodeSelectionFailure != null) {
                            // Copy selection failure
                            this.targetNodeSelectionFailure = otherEdge.targetNodeSelectionFailure;
                            return;
                        }
                    }
                }
            }
            for (NodeState node : targetComponent.getNodes()) {
                if (node.isSelected() && !node.isRoot()) {
                    targetNodes.add(node);
                }
            }
            return;
        }

        GraphVariantSelectionResult targetVariants;
        try {
            targetVariants = selectTargetVariants(targetComponentState);
        } catch (AttributeMergingException mergeError) {
            targetNodeSelectionFailure = new ModuleVersionResolveException(getRequested(), () -> {
                Attribute<?> attribute = mergeError.getAttribute();
                Object constraintValue = mergeError.getLeftValue();
                Object dependencyValue = mergeError.getRightValue();
                return "Inconsistency between attributes of a constraint and a dependency, on attribute '" + attribute + "' : dependency requires '" + dependencyValue + "' while constraint required '" + constraintValue + "'";
            });
            return;
        } catch (Exception t) {
            // Failure to select the target variant/configurations from this component, given the dependency attributes/metadata.
            targetNodeSelectionFailure = new ModuleVersionResolveException(getRequested(), t);
            return;
        }

        for (VariantGraphResolveState targetVariant : targetVariants.getVariants()) {
            NodeState targetNodeState = resolveState.getNode(targetComponent, targetVariant, targetVariants.isSelectedByVariantAwareResolution());
            while (targetNodeState.getReplacement() != null) {
                targetNodeState = targetNodeState.getReplacement();
            }
            this.targetNodes.add(targetNodeState);
        }
    }

    /**
     * Determine which variants of a given target component that this edge should point to.
     */
    private GraphVariantSelectionResult selectTargetVariants(ComponentGraphResolveState targetComponentState) {
        GraphVariantSelector variantSelector = resolveState.getVariantSelector();
        ImmutableAttributes attributes = resolveState.getAttributesFactory().concat(resolveState.getConsumerAttributes(), getAttributes());
        ImmutableAttributesSchema consumerSchema = resolveState.getConsumerSchema();

        // First allow the dependency to override variant selection, if it has a special
        // variant selection mechanism for its ecosystem.
        List<? extends VariantGraphResolveState> overrideVariants = dependencyMetadata.overrideVariantSelection(
            variantSelector,
            attributes,
            targetComponentState,
            consumerSchema
        );

        if (overrideVariants != null) {
            return new GraphVariantSelectionResult(overrideVariants, false);
        }

        // Use attribute matching if it is supported.
        if (!targetComponentState.getCandidatesForGraphVariantSelection().getVariantsForAttributeMatching().isEmpty()) {
            Set<CapabilitySelector> capabilitySelectors = dependencyMetadata.getSelector().getCapabilitySelectors();
            VariantGraphResolveState selected = variantSelector.selectByAttributeMatching(
                attributes,
                capabilitySelectors,
                targetComponentState,
                consumerSchema,
                dependencyMetadata.getArtifacts()
            );

            return new GraphVariantSelectionResult(Collections.singletonList(selected), true);
        }

        // Otherwise, for target components that don't support attribute matching, fallback to legacy variant selection.
        List<? extends VariantGraphResolveState> legacyVariants = dependencyMetadata.selectLegacyVariants(
            variantSelector,
            attributes,
            targetComponentState,
            consumerSchema
        );

        return new GraphVariantSelectionResult(legacyVariants, false);
    }

    @Override
    public boolean isFromLock() {
        return dependencyState.isFromLock();
    }

    public static class GraphVariantSelectionResult {

        private final List<? extends VariantGraphResolveState> variants;
        private final boolean selectedByVariantAwareResolution;

        public GraphVariantSelectionResult(List<? extends VariantGraphResolveState> variants, boolean selectedByVariantAwareResolution) {
            this.variants = variants;
            this.selectedByVariantAwareResolution = selectedByVariantAwareResolution;
        }

        public List<? extends VariantGraphResolveState> getVariants() {
            return variants;
        }

        public boolean isSelectedByVariantAwareResolution() {
            return selectedByVariantAwareResolution;
        }

    }

    @Override
    public ExcludeSpec getExclusions() {
        if (cachedExclusions == null) {
            computeExclusions();
        }
        return cachedExclusions;
    }

    private void computeExclusions() {
        List<ExcludeMetadata> excludes = dependencyMetadata.getExcludes();
        if (excludes.isEmpty()) {
            cachedExclusions = transitiveExclusions;
        } else {
            computeExclusionsWhenExcludesPresent(excludes);
        }
    }

    private void computeExclusionsWhenExcludesPresent(List<ExcludeMetadata> excludes) {
        ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
        ExcludeSpec edgeExclusions = moduleExclusions.excludeAny(excludes);
        cachedExclusions = moduleExclusions.excludeAny(edgeExclusions, transitiveExclusions);
    }

    ExcludeSpec getEdgeExclusions() {
        if (cachedEdgeExclusions == null) {
            List<ExcludeMetadata> excludes = dependencyMetadata.getExcludes();
            ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
            if (excludes.isEmpty()) {
                return moduleExclusions.nothing();
            }
            cachedEdgeExclusions = moduleExclusions.excludeAny(excludes);
        }
        return cachedEdgeExclusions;
    }

    @Override
    public boolean contributesArtifacts() {
        return !isConstraint;
    }

    @Override
    public ComponentSelector getRequested() {
        return resolveState.desugarSelector(dependencyState.getRequested());
    }

    @Override
    public ModuleVersionResolveException getFailure() {
        if (targetNodeSelectionFailure != null) {
            return targetNodeSelectionFailure;
        }
        ModuleVersionResolveException selectorFailure = selector.getFailure();
        if (selectorFailure != null) {
            return selectorFailure;
        }
        ComponentState selectedComponent = getSelectedComponent();
        if (selectedComponent == null) {
            ModuleSelectors<SelectorState> selectors = selector.getTargetModule().getSelectors();
            for (SelectorState state : selectors) {
                selectorFailure = state.getFailure();
                if (selectorFailure != null) {
                    return selectorFailure;
                }
            }
            throw new IllegalStateException("Expected to find a selector with a failure but none was found");
        }
        return selectedComponent.getMetadataResolveFailure();
    }

    @Override
    public long getTargetComponentId() {
        NodeState targetNode = getFirstTargetNode();
        if (targetNode != null) {
            return targetNode.getComponent().getResultId();
        }
        throw new IllegalStateException("No target component for edge " + this);
    }

    @Override
    public boolean isTargetVirtualPlatform() {
        NodeState targetNode = getFirstTargetNode();
        if (targetNode != null) {
            return targetNode.getComponent().getModule().isVirtualPlatform();
        }
        return false;
    }

    @Override
    public long getTargetVariantId() {
        NodeState targetNode = getFirstTargetNode();
        if (targetNode != null) {
            return targetNode.getNodeId();
        }
        throw new IllegalStateException("No target variant for edge " + this);
    }

    public Collection<NodeState> getTargetNodes() {
        return targetNodes;
    }

    public @Nullable NodeState getFirstTargetNode() {
        if (targetNodes.isEmpty()) {
            return null;
        }

        return targetNodes.get(0);
    }

    @Override
    public ComponentSelectionReasonInternal getReason() {
        ImmutableSet.Builder<ComponentSelectionDescriptorInternal> dependencyReasons = ImmutableSet.builderWithExpectedSize(4);
        visitSelectionReasons(dependencyReasons::add);
        return ComponentSelectionReasons.of(dependencyReasons.build());
    }

    @Override
    public void visitSelectionReasons(Consumer<ComponentSelectionDescriptorInternal> visitor) {
        visitor.accept(getMainReason());

        ImmutableList<ComponentSelectionDescriptorInternal> ruleDescriptors = dependencyState.getRuleDescriptors();
        if (!ruleDescriptors.isEmpty()) {
            ruleDescriptors.forEach(visitor);
        }

        if (dependencyState.isForced()) {
            visitor.accept(FORCED);
        }
    }

    private ComponentSelectionDescriptorInternal getMainReason() {
        if (selector != null && selector.isVersionProvidedByAncestor()) {
            return withDependencyReason(BY_ANCESTOR);
        } else if (dependencyState.getDependency().isConstraint()) {
            return withSelectorReason(withDependencyReason(CONSTRAINT));
        } else {
            return withSelectorReason(withDependencyReason(REQUESTED));
        }
    }

    private ComponentSelectionDescriptorInternal withDependencyReason(ComponentSelectionDescriptorInternal dependencyDescriptor) {
        String reason = dependencyState.getDependency().getReason();
        if (reason != null) {
            dependencyDescriptor = dependencyDescriptor.withDescription(Describables.of(reason));
        }
        return dependencyDescriptor;
    }

    private ComponentSelectionDescriptorInternal withSelectorReason(ComponentSelectionDescriptorInternal descriptor) {
        return selector == null ? descriptor : selector.maybeEnhanceReason(descriptor);
    }

    @Override
    public boolean isConstraint() {
        return isConstraint;
    }

    @Nullable
    private ComponentState getSelectedComponent() {
        return selector.getTargetModule().getSelected();
    }

    DependencyState getDependencyState() {
        return dependencyState;
    }

    public void updateTransitiveExcludes(ExcludeSpec newResolutionFilter) {
        if (isConstraint) {
            // Constraint do not carry excludes on a path
            return;
        }
        transitiveExclusions = newResolutionFilter;
        cachedExclusions = null;
    }

    public void updateTransitiveExcludesAndRequeueTargetNodes(ExcludeSpec newResolutionFilter) {
        updateTransitiveExcludes(newResolutionFilter);
        for (NodeState targetNode : targetNodes) {
            targetNode.clearTransitiveExclusionsAndEnqueue();
        }
    }

    void recomputeSelectorAndRequeueTargetNodes(StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        if (computeSelector(ancestorsStrictVersions, false)) {
            discoveredEdges.add(this);
        }
        // TODO: If we compute the selector for this edge and it changes, we shouldn't add the (potentially) invalid target nodes to the queue.
        // If we added this edge to `discoveredEdges`, then we will recompute target nodes and there is no point in adding the current target nodes to the queue.
        for (NodeState targetNode : targetNodes) {
            resolveState.onMoreSelected(targetNode);
        }
    }

    @Nullable
    ExcludeSpec getTransitiveExclusions() {
        return transitiveExclusions;
    }

    public void markUnattached() {
        this.unattached = true;
    }

    public void markNotUnattached() {
        this.unattached = false;
    }

    public boolean isUnattached() {
        return unattached;
    }

    /**
     * Indicates whether the edge is currently listed as outgoing in a node.
     * It can be either a full edge or an edge to a virtual platform.
     *
     * @return true if used, false otherwise
     */
    boolean isUsed() {
        return selector != null;
    }

    public boolean isArtifactOnlyEdge() {
        return !isTransitive && !dependencyMetadata.getArtifacts().isEmpty();
    }
}
