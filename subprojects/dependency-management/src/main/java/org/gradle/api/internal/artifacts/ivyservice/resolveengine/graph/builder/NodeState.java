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
import com.google.common.collect.Lists;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a node in the dependency graph.
 */
class NodeState implements DependencyGraphNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private static final Spec<EdgeState> TRANSITIVE_EDGES = new Spec<EdgeState>() {
        @Override
        public boolean isSatisfiedBy(EdgeState edge) {
            return edge.isTransitive();
        }
    };

    private final Long resultId;
    private final ComponentState component;
    private final List<EdgeState> incomingEdges = Lists.newArrayList();
    private final List<EdgeState> outgoingEdges = Lists.newArrayList();
    private final ResolvedConfigurationIdentifier id;

    private final ConfigurationMetadata metaData;
    private final ResolveState resolveState;
    private final boolean isTransitive;

    ModuleExclusion previousTraversalExclusions;
    // In opposite to outgoing edges, virtual edges are for now pretty rare, so they are created lazily
    private List<EdgeState> virtualEdges;
    private boolean queued;

    NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
        this.resultId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.metaData = md;
        this.isTransitive = metaData.isTransitive();
        component.addConfiguration(this);
    }

    // the enqueue and dequeue methods are used for performance reasons
    // in order to avoid tracking the set of enqueued nodes
    boolean enqueue() {
        if (queued) {
            return false;
        }
        queued = true;
        return true;
    }

    NodeState dequeue() {
        queued = false;
        return this;
    }

    ComponentState getComponent() {
        return component;
    }

    @Override
    public Long getNodeId() {
        return resultId;
    }

    @Override
    public boolean isRoot() {
        return false;
    }

    @Override
    public ResolvedConfigurationIdentifier getResolvedConfigurationId() {
        return id;
    }

    @Override
    public ComponentState getOwner() {
        return component;
    }

    @Override
    public List<EdgeState> getIncomingEdges() {
        return incomingEdges;
    }

    @Override
    public List<EdgeState> getOutgoingEdges() {
        return outgoingEdges;
    }

    @Override
    public ConfigurationMetadata getMetadata() {
        return metaData;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        if (metaData instanceof LocalConfigurationMetadata) {
            // Only when this node has a transitive incoming edge
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.isTransitive()) {
                    return ((LocalConfigurationMetadata) metaData).getFiles();
                }
            }
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", component, id.getConfiguration());
    }

    public boolean isTransitive() {
        return isTransitive;
    }

    /**
     * Visits all of the dependencies that originate on this node, adding them as outgoing edges.
     * The {@link #outgoingEdges} collection is populated, as is the `discoveredEdges` parameter.
     *
     * @param discoveredEdges A collector for visited edges.
     */
    public void visitOutgoingDependencies(Collection<EdgeState> discoveredEdges) {
        // If this configuration's version is in conflict, do not traverse.
        // If none of the incoming edges are transitive, remove previous state and do not traverse.
        // If not traversed before, simply add all selected outgoing edges (either hard or pending edges)
        // If traversed before:
        //      If net exclusions for this node have not changed, ignore
        //      If net exclusions for this node not changed, remove previous state and traverse outgoing edges again.

        if (!component.isSelected()) {
            LOGGER.debug("version for {} is not selected. ignoring.", this);
            return;
        }

        // Check if this node is still included in the graph, by looking at incoming edges.
        List<EdgeState> transitiveIncoming = getTransitiveIncomingEdges();

        // Check if there are any transitive incoming edges at all. Don't traverse if not.
        if (transitiveIncoming.isEmpty() && !isRoot()) {
            handleNonTransitiveNode(discoveredEdges);
            return;
        }

        // Determine the net exclusion for this node, by inspecting all transitive incoming edges
        ModuleExclusion resolutionFilter = getModuleResolutionFilter(incomingEdges);

        // Check if the was previously traversed with the same net exclusion
        if (previousTraversalExclusions != null && previousTraversalExclusions.excludesSameModulesAs(resolutionFilter)) {
            // Was previously traversed, and no change to the set of modules that are linked by outgoing edges.
            // Don't need to traverse again, but hang on to the new filter since it may change the set of excluded artifacts.
            LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
            previousTraversalExclusions = resolutionFilter;
            return;
        }

        // Clear previous traversal state, if any
        if (previousTraversalExclusions != null) {
            removeOutgoingEdges();
        }

        visitDependencies(resolutionFilter, discoveredEdges);
        visitOwners(discoveredEdges);
    }

    /**
     * Removes outgoing edges from no longer transitive node
     * Also process {@code belongsTo} if node still has edges at all.
     *
     * @param discoveredEdges In/Out parameter collecting dependencies or platforms
     */
    private void handleNonTransitiveNode(Collection<EdgeState> discoveredEdges) {
        // If node was previously traversed, need to remove outgoing edges.
        if (previousTraversalExclusions != null) {
            removeOutgoingEdges();
        }
        if (!incomingEdges.isEmpty()) {
            LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
            visitOwners(discoveredEdges);
        } else {
            LOGGER.debug("{} has no incoming edges. ignoring.", this);
        }
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private void visitDependencies(ModuleExclusion resolutionFilter, Collection<EdgeState> discoveredEdges) {
        PendingDependenciesVisitor pendingDepsVisitor = resolveState.newPendingDependenciesVisitor();
        try {
            for (DependencyMetadata dependency : metaData.getDependencies()) {
                DependencyState dependencyState = new DependencyState(dependency, resolveState.getComponentSelectorConverter());
                if (isExcluded(resolutionFilter, dependencyState)) {
                    continue;
                }
                dependencyState = maybeSubstitute(dependencyState, resolveState.getDependencySubstitutionApplicator());
                if (!pendingDepsVisitor.maybeAddAsPendingDependency(this, dependencyState)) {
                    EdgeState dependencyEdge = new EdgeState(this, dependencyState, resolutionFilter, resolveState);
                    outgoingEdges.add(dependencyEdge);
                    discoveredEdges.add(dependencyEdge);
                    dependencyEdge.getSelector().use();
                }
            }
            previousTraversalExclusions = resolutionFilter;
        } finally {
            // If there are 'pending' dependencies that share a target with any of these outgoing edges,
            // then reset the state of the node that owns those dependencies.
            // This way, all edges of the node will be re-processed.
            pendingDepsVisitor.complete();
        }
    }

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     *
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(Collection<EdgeState> discoveredEdges) {
        ImmutableList<? extends ComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            PendingDependenciesVisitor visitor = resolveState.newPendingDependenciesVisitor();
            for (ComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier platformId = (ModuleComponentIdentifier) owner;
                    final ModuleComponentSelector cs = DefaultModuleComponentSelector.newSelector(platformId.getModuleIdentifier(), platformId.getVersion());

                    // There are 2 possibilities here:
                    // 1. the "platform" referenced is a real module, in which case we directly add it to the graph
                    // 2. the "platform" is a virtual, constructed thing, in which case we add virtual edges to the graph
                    addPlatformEdges(discoveredEdges, platformId, cs);
                    visitor.markNotPending(platformId.getModuleIdentifier());
                }
            }
            visitor.complete();
        }
    }

    private void addPlatformEdges(Collection<EdgeState> discoveredEdges, ModuleComponentIdentifier platformComponentIdentifier, ModuleComponentSelector platformSelector) {
        PotentialEdge potentialEdge = PotentialEdge.of(resolveState, this, platformComponentIdentifier, platformSelector, platformComponentIdentifier);
        ComponentResolveMetadata metadata = potentialEdge.metadata;
        VirtualPlatformState virtualPlatformState = null;
        if (metadata == null || metadata instanceof LenientPlatformResolveMetadata) {
            virtualPlatformState = potentialEdge.component.getModule().getPlatformState();
            virtualPlatformState.participatingModule(component.getModule());
        }
        if (metadata == null) {
            // the platform doesn't exist, so we're building a lenient one
            metadata = new LenientPlatformResolveMetadata(platformComponentIdentifier, potentialEdge.toModuleVersionId, virtualPlatformState, this, resolveState);
            potentialEdge.component.setMetadata(metadata);
            // And now let's make sure we do not have another version of that virtual platform missing its metadata
            potentialEdge.component.getModule().maybeCreateVirtualMetadata(resolveState);
        }
        if (virtualEdges == null) {
            virtualEdges = Lists.newArrayList();
        }
        EdgeState edge = potentialEdge.edge;
        virtualEdges.add(edge);
        discoveredEdges.add(edge);
        edge.getSelector().use();
    }


    /**
     * Execute any dependency substitution rules that apply to this dependency.
     *
     * This may be better done as a decorator on ConfigurationMetadata.getDependencies()
     */
    static DependencyState maybeSubstitute(DependencyState dependencyState, DependencySubstitutionApplicator dependencySubstitutionApplicator) {
        DependencySubstitutionApplicator.SubstitutionResult substitutionResult = dependencySubstitutionApplicator.apply(dependencyState.getDependency());
        if (substitutionResult.hasFailure()) {
            dependencyState.failure = new ModuleVersionResolveException(dependencyState.getRequested(), substitutionResult.getFailure());
            return dependencyState;
        }

        DependencySubstitutionInternal details = substitutionResult.getResult();
        if (details != null && details.isUpdated()) {
            return dependencyState.withTarget(details.getTarget(), details.getRuleDescriptors());
        }
        return dependencyState;
    }

    /**
     * Returns the set of incoming edges that are transitive. Most edges are transitive, so the implementation is optimized for this case.
     */
    private List<EdgeState> getTransitiveIncomingEdges() {
        if (isRoot()) {
            return incomingEdges;
        }
        for (EdgeState incomingEdge : incomingEdges) {
            if (!incomingEdge.isTransitive()) {
                // Have a non-transitive edge: return a filtered list
                return CollectionUtils.filter(incomingEdges, TRANSITIVE_EDGES);
            }
        }
        // All edges are transitive, no need to construct a filtered list.
        return incomingEdges;
    }

    private boolean isExcluded(ModuleExclusion selector, DependencyState dependencyState) {
        DependencyMetadata dependency = dependencyState.getDependency();
        if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
            LOGGER.debug("{} is filtered.", dependency);
            return true;
        }
        if (selector == ModuleExclusions.excludeNone()) {
            return false;
        }
        ModuleIdentifier targetModuleId = dependencyState.getModuleIdentifier();
        if (selector.excludeModule(targetModuleId)) {
            LOGGER.debug("{} is excluded from {}.", targetModuleId, this);
            return true;
        }

        return false;
    }

    public void addIncomingEdge(EdgeState dependencyEdge) {
        incomingEdges.add(dependencyEdge);
        resolveState.onMoreSelected(this);
    }

    public void removeIncomingEdge(EdgeState dependencyEdge) {
        incomingEdges.remove(dependencyEdge);
        resolveState.onFewerSelected(this);
    }

    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    public boolean shouldIncludedInGraphResult() {
        return isSelected() && !component.getModule().isVirtualPlatform();
    }

    private ModuleExclusion getModuleResolutionFilter(List<EdgeState> incomingEdges) {
        ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
        ModuleExclusion nodeExclusions = moduleExclusions.excludeAny(metaData.getExcludes());
        if (incomingEdges.isEmpty()) {
            return nodeExclusions;
        }

        ModuleExclusion edgeExclusions = null;

        for (EdgeState dependencyEdge : incomingEdges) {
            if (dependencyEdge.isTransitive()) {
                // Transitive dependency
                edgeExclusions = excludedByBoth(edgeExclusions, dependencyEdge.getExclusions());
            } else if (dependencyEdge.getDependencyMetadata().isConstraint()) {
                // Constraint: only consider explicit exclusions declared for this constraint
                ModuleExclusion constraintExclusions = dependencyEdge.getEdgeExclusions();
                nodeExclusions = excludedByEither(nodeExclusions, constraintExclusions);
            }
        }
        return excludedByEither(edgeExclusions, nodeExclusions);
    }

    private ModuleExclusion excludedByBoth(ModuleExclusion one, ModuleExclusion two) {
        if (one == null) {
            return two;
        }
        if (two == null) {
            return one;
        }
        return resolveState.getModuleExclusions().union(one, two);
    }

    private ModuleExclusion excludedByEither(ModuleExclusion one, ModuleExclusion two) {
        if (one == null) {
            return two;
        }
        if (two == null) {
            return one;
        }
        return resolveState.getModuleExclusions().intersect(one, two);
    }

    private void removeOutgoingEdges() {
        if (!outgoingEdges.isEmpty()) {
            for (EdgeState outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release();
                outgoingDependency.maybeDecreaseHardEdgeCount();
            }
        }
        outgoingEdges.clear();
        if (virtualEdges != null) {
            for (EdgeState outgoingDependency : virtualEdges) {
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release();
            }
        }
        virtualEdges = null;
        previousTraversalExclusions = null;
    }

    public void restart(ComponentState selected) {
        // Restarting this configuration after conflict resolution.
        // If this configuration belongs to the select version, queue ourselves up for traversal.
        // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
        if (component == selected) {
            resolveState.onMoreSelected(this);
        } else {
            if (!incomingEdges.isEmpty()) {
                restartIncomingEdges();
            }
        }
    }

    private void restartIncomingEdges() {
        if (incomingEdges.size() == 1) {
            EdgeState singleEdge = incomingEdges.get(0);
            singleEdge.restart();
        } else {
            for (EdgeState dependency : new ArrayList<>(incomingEdges)) {
                dependency.restart();
            }
        }
        incomingEdges.clear();
    }

    public void deselect() {
        removeOutgoingEdges();
    }

    void resetSelectionState() {
        if (previousTraversalExclusions != null) {
            previousTraversalExclusions = null;
            outgoingEdges.clear();
            virtualEdges = null;
            resolveState.onFewerSelected(this);
        }
    }

    public ImmutableAttributesFactory getAttributesFactory() {
        return resolveState.getAttributesFactory();
    }

    /**
     * Invoked when this node is back to being a pending dependency.
     * There may be some incoming edges left at that point, but they must all be coming from constraints.
     * @param pendingDependencies
     */
    public void clearConstraintEdges(PendingDependencies pendingDependencies) {
        for (EdgeState incomingEdge : incomingEdges) {
            assert incomingEdge.getDependencyMetadata().isConstraint();
            incomingEdge.getSelector().release();
            NodeState from = incomingEdge.getFrom();
            from.getOutgoingEdges().remove(incomingEdge);
            pendingDependencies.addNode(from);
        }
        incomingEdges.clear();
    }
}
