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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphEdge;
import org.gradle.api.internal.attributes.AttributeMergingException;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.GraphVariantSelectionResult;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.resolve.ModuleVersionResolveException;

import javax.annotation.Nullable;
import java.util.LinkedList;
import java.util.List;

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
    private final int hashCode;

    private SelectorState selector;
    private ModuleVersionResolveException targetNodeSelectionFailure;
    private ImmutableAttributes cachedAttributes;
    private ExcludeSpec transitiveExclusions;
    private ExcludeSpec cachedEdgeExclusions;
    private ExcludeSpec cachedExclusions;

    private NodeState resolvedVariant;
    private boolean unattached;
    private boolean used;

    EdgeState(NodeState from, DependencyState dependencyState, ExcludeSpec transitiveExclusions, ResolveState resolveState) {
        this.from = from;
        this.dependencyState = dependencyState;
        this.dependencyMetadata = dependencyState.getDependency();
        // The accumulated exclusions that apply to this edge based on the path from the root
        this.transitiveExclusions = transitiveExclusions;
        this.resolveState = resolveState;
        this.isTransitive = from.isTransitive() && dependencyMetadata.isTransitive();
        this.isConstraint = dependencyMetadata.isConstraint();
        this.hashCode = computeHashCode();
    }

    private int computeHashCode() {
        int hashCode = from.hashCode();
        hashCode = 31 * hashCode + dependencyState.hashCode();
        if (transitiveExclusions != null) {
            hashCode = 31 * hashCode + transitiveExclusions.hashCode();
        }
        return hashCode;
    }

    void computeSelector() {
        this.selector = resolveState.getSelector(dependencyState, from.versionProvidedByAncestors(dependencyState));
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
        if (!selector.isResolved() || selector.getFailure() != null) {
            return null;
        }
        return getSelectedComponent();
    }

    @Override
    public SelectorState getSelector() {
        return selector;
    }

    @Override
    public boolean isTransitive() {
        return isTransitive;
    }

    void attachToTargetConfigurations() {
        ComponentState targetComponent = getTargetComponent();
        if (targetComponent == null || !isUsed()) {
            // The selector failed or the module has been deselected or the edge source has been deselected. Do not attach.
            return;
        }

        if (isConstraint) {
            // Need to double check that the target still has hard edges to it
            ModuleResolveState module = targetComponent.getModule();
            if (module.isPending()) {
                selector.getTargetModule().removeUnattachedDependency(this);
                from.makePending(this);
                module.registerConstraintProvider(from);
                return;
            }
        }

        calculateTargetConfigurations(targetComponent);
        for (NodeState targetConfiguration : targetNodes) {
            targetConfiguration.addIncomingEdge(this);
        }
        if (!targetNodes.isEmpty()) {
            selector.getTargetModule().removeUnattachedDependency(this);
        }
    }

    void cleanUpOnSourceChange(NodeState source) {
        removeFromTargetConfigurations();
        maybeDecreaseHardEdgeCount(source);
        selector.getTargetModule().removeUnattachedDependency(this);
        selector.release(resolveState.getConflictTracker());
    }

    void removeFromTargetConfigurations() {
        if (!targetNodes.isEmpty()) {
            for (NodeState targetConfiguration : targetNodes) {
                targetConfiguration.removeIncomingEdge(this);
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
        targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), err);
    }


    public void restart() {
        if (from.isSelected()) {
            restartInternal(false);
        }
    }

    public void restartConnected() {
        if (from.isSelected() && isUsed()) {
            restartInternal(true);
        }
    }

    private void restartInternal(boolean checkUnattached) {
        removeFromTargetConfigurations();
        // We now have corner cases that can lead to this restart not succeeding
        if (checkUnattached && !isUnattached()) {
            selector.getTargetModule().addUnattachedDependency(this);
        }
        attachToTargetConfigurations();
    }

    @Override
    public ImmutableAttributes getAttributes() {
        assert cachedAttributes != null;
        return cachedAttributes;
    }

    private ImmutableAttributes safeGetAttributes() throws AttributeMergingException {
        ModuleResolveState module = selector.getTargetModule();
        cachedAttributes = module.mergedConstraintsAttributes(dependencyState.getDependency().getSelector().getAttributes());
        return cachedAttributes;
    }

    private void calculateTargetConfigurations(ComponentState targetComponent) {
        ComponentGraphResolveState targetComponentState = targetComponent.getResolveStateOrNull();
        targetNodes.clear();
        targetNodeSelectionFailure = null;
        if (targetComponentState == null) {
            targetComponent.getModule().getPlatformState().addOrphanEdge(this);
            // Broken version
            return;
        }
        if (isConstraint && !isVirtualDependency()) {
            List<NodeState> nodes = targetComponent.getNodes();
            for (NodeState node : nodes) {
                if (node.isSelected() && !node.isRoot()) {
                    targetNodes.add(node);
                }
            }
            if (targetNodes.isEmpty()) {
                // There is a chance we could not attach target configurations previously
                List<EdgeState> unattachedDependencies = targetComponent.getModule().getUnattachedDependencies();
                if (!unattachedDependencies.isEmpty()) {
                    for (EdgeState otherEdge : unattachedDependencies) {
                        if (otherEdge != this && !otherEdge.isConstraint()) {
                            otherEdge.attachToTargetConfigurations();
                            if (otherEdge.targetNodeSelectionFailure != null) {
                                // Copy selection failure
                                this.targetNodeSelectionFailure = otherEdge.targetNodeSelectionFailure;
                                return;
                            }
                            break;
                        }
                    }
                }
                for (NodeState node : nodes) {
                    if (node.isSelected() && !node.isRoot()) {
                        targetNodes.add(node);
                    }
                }
            }
            return;
        }

        GraphVariantSelectionResult targetVariants;
        try {
            ImmutableAttributes attributes = resolveState.getRoot().getMetadata().getAttributes();
            attributes = resolveState.getAttributesFactory().concat(attributes, safeGetAttributes());
            targetVariants = dependencyMetadata.selectVariants(resolveState.getVariantSelector(), attributes, targetComponentState, resolveState.getAttributesSchema(), dependencyState.getDependency().getSelector().getRequestedCapabilities());
        } catch (AttributeMergingException mergeError) {
            targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), () -> {
                Attribute<?> attribute = mergeError.getAttribute();
                Object constraintValue = mergeError.getLeftValue();
                Object dependencyValue = mergeError.getRightValue();
                return "Inconsistency between attributes of a constraint and a dependency, on attribute '" + attribute + "' : dependency requires '" + dependencyValue + "' while constraint required '" + constraintValue + "'";
            });
            return;
        } catch (Exception t) {
            // Failure to select the target variant/configurations from this component, given the dependency attributes/metadata.
            targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyState.getRequested(), t);
            return;
        }
        for (VariantGraphResolveState targetVariant : targetVariants.getVariants()) {
            NodeState targetNodeState = resolveState.getNode(targetComponent, targetVariant, targetVariants.isSelectedByVariantAwareResolution());
            this.targetNodes.add(targetNodeState);
        }
    }

    private boolean isVirtualDependency() {
        return selector.getDependencyMetadata() instanceof LenientPlatformDependencyMetadata;
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
    public Long getSelected() {
        return getSelectedComponent().getResultId();
    }

    @Override
    public boolean isTargetVirtualPlatform() {
        ComponentState selectedComponent = getSelectedComponent();
        return selectedComponent != null && selectedComponent.getModule().isVirtualPlatform();
    }

    boolean hasSelectedVariant() {
        return resolvedVariant != null || !findTargetNodes().isEmpty();
    }

    @Nullable
    @Override
    public Long getSelectedVariant() {
        NodeState node = getSelectedNode();
        if (node == null) {
            return null;
        } else {
            return node.getNodeId();
        }
    }

    @Nullable
    public NodeState getSelectedNode() {
        if (resolvedVariant != null) {
            return resolvedVariant;
        }
        List<NodeState> targetNodes = findTargetNodes();
        assert !targetNodes.isEmpty();
        for (NodeState targetNode : targetNodes) {
            if (targetNode.isSelected()) {
                resolvedVariant = targetNode;
                return resolvedVariant;
            }
        }
        return null;
    }

    private List<NodeState> findTargetNodes() {
        List<NodeState> targetNodes = this.targetNodes;
        if (targetNodes.isEmpty()) {
            // happens for substituted dependencies
            ComponentState targetComponent = getTargetComponent();
            if (targetComponent != null) {
                targetNodes = targetComponent.getNodes();
            }
        }
        return targetNodes;
    }

    @Override
    public ComponentSelectionReason getReason() {
        return selector.getSelectionReason();
    }

    @Override
    public boolean isConstraint() {
        return isConstraint;
    }

    @Override
    public Long getFromVariant() {
        return from.getNodeId();
    }

    @Nullable
    private ComponentState getSelectedComponent() {
        return selector.getTargetModule().getSelected();
    }

    @Override
    public Dependency getOriginalDependency() {
        if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
            return ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
        }
        return null;
    }

    void maybeDecreaseHardEdgeCount(NodeState removalSource) {
        if (!isConstraint) {
            selector.getTargetModule().decreaseHardEdgeCount(removalSource);
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o;
        // Edge states are deduplicated, this is a performance optimization
    }

    @Override
    public int hashCode() {
        return hashCode;
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
        for (NodeState targetNode : targetNodes) {
            targetNode.updateTransitiveExcludes();
        }
    }

    @Nullable
    ExcludeSpec getTransitiveExclusions() {
        return transitiveExclusions;
    }

    public void markUnattached() {
        this.unattached = true;
    }

    public void markAttached() {
        this.unattached = false;
    }

    public boolean isUnattached() {
        return unattached;
    }

    void markUsed() {
        this.used = true;
    }

    void markUnused() {
        this.used = false;
    }

    /**
     * Indicates whether the edge is currently listed as outgoing in a node.
     * It can be either a full edge or an edge to a virtual platform.
     *
     * @return true if used, false otherwise
     */
    boolean isUsed() {
        return used;
    }

    public boolean isArtifactOnlyEdge() {
        return !isTransitive && !dependencyMetadata.getArtifacts().isEmpty();
    }
}
