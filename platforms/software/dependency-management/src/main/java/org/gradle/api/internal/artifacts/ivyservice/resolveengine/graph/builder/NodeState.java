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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.SubstitutionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.Pair;
import org.gradle.internal.Try;
import org.gradle.internal.collect.PersistentSet;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a node in the dependency graph.
 */
public class NodeState implements DependencyGraphNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeState.class);
    private final long nodeId;
    private final ComponentState component;
    private final List<EdgeState> incomingEdges = new ArrayList<>();
    private final List<EdgeState> outgoingEdges = new ArrayList<>();

    private final VariantGraphResolveState variantState;
    private final VariantGraphResolveMetadata metadata;
    private final ResolveState resolveState;
    private final ModuleExclusions moduleExclusions;
    private final boolean isTransitive;
    private final boolean selectedByVariantAwareResolution;
    private final boolean dependenciesMayChange;

    private boolean queued;

    /**
     * The number of unresolved capability conflicts this node is involved in.
     */
    private int numCapabilityConflicts;

    /**
     * The node this node has been replaced by in a capability conflict, if this
     * node has been previously involved in a resolved capability conflict and
     * has lost that conflict.
     * <p>
     * This value may be stale. This pointer records the result of capability conflict resolution
     * and only remains valid while the winner's component is present in the graph. Nothing updates this
     * value when a participating module changes selection. This field must only be read by
     * {@link #maybeResolveCapabilityReplacement()}, which ensures target replacement nodes are still valid.
     */
    private @Nullable NodeState capabilityReplacement;
    private @Nullable Pair<Capability, Collection<NodeState>> capabilityReject;

    private int transitiveEdgeCount;
    private @Nullable Set<ModuleIdentifier> upcomingNoLongerPendingConstraints;

    /**
     * Virtual platforms require their constraints to be recomputed each time, as each module addition
     * can cause a shift in versions. Therefore, if this true, we perform a full dependency visit even
     * though we've already visited this node's dependencies before.
     */
    private boolean virtualPlatformNeedsRefresh;
    private @Nullable Set<EdgeState> edgesToRecompute;
    private @Nullable Multimap<ModuleIdentifier, EdgeState> potentiallyActivatedConstraints;

    // Caches the list of edges
    private @Nullable List<EdgeState> cachedEdges;

    /**
     * Caches the list of edges which are NOT excluded by {@link #cachedFilteredEdgesResolutionFilter}.
     */
    private @Nullable List<EdgeState> cachedFilteredEdges;

    /**
     * The resolution filter {@link #cachedFilteredEdges} was computed with.
     */
    private @Nullable ExcludeSpec cachedFilteredEdgesResolutionFilter;

    // exclusions optimizations
    private @Nullable ExcludeSpec cachedNodeExclusions;
    private int previousIncomingEdgeCount;
    private long previousIncomingHash;
    private long incomingHash;
    private @Nullable ExcludeSpec cachedModuleResolutionFilter;

    /**
     * Whether a visit of our dependencies registered our outgoing edges. It is false before our first visit, after our
     * outgoing edges are removed, and after a visit that only created the edges to our virtual platforms.
     */
    private boolean visitedDependencies = false;

    /**
     * The resolution filter our outgoing edges were registered with, or null if none were registered since they were
     * last removed.
     */
    private @Nullable ExcludeSpec previousResolutionFilter;

    /**
     * The edges which were NOT excluded by {@link #previousResolutionFilter}, from which our outgoing edges to our
     * dependencies were registered.
     */
    private @Nullable List<EdgeState> previousFilteredEdges;

    /**
     * The strict versions inherited from parents our outgoing edges were registered with.
     */
    private @Nullable StrictVersionConstraints previousAncestorsStrictVersions;

    /**
     * The transitive strict versions inherited from parents, or null while unknown. A known value can only be
     * larger than the value once resolution is done, never smaller. It shrinks as parents' inheritable strict versions
     * become known, and it is reset when a change could make it grow.
     */
    @VisibleForTesting
    @Nullable StrictVersionConstraints ancestorsStrictVersions;

    /**
     * Our own strict versions, which the edges in {@link #cachedFilteredEdges} declare.
     */
    @VisibleForTesting
    @Nullable StrictVersionConstraints ownStrictVersions;

    /**
     * The strict versions endorsed by this node, as of its last visit, or null while unknown.
     * This node is queued whenever an outgoing endorsing edge is added or removed, or the target endorsed
     * node's own strict versions change.
     */
    private @Nullable StrictVersionConstraints endorsedStrictVersions;

    /**
     * The strict versions children can inherit from this node, as of the end of its last visit, or null if unknown.
     */
    private @Nullable StrictVersionConstraints inheritableStrictVersions;

    private boolean findingExternalVariants;

    public NodeState(long nodeId, ComponentState component, ResolveState resolveState, VariantGraphResolveState variant, boolean selectedByVariantAwareResolution) {
        this.nodeId = nodeId;
        this.component = component;
        this.resolveState = resolveState;
        this.variantState = variant;
        this.metadata = variant.getMetadata();
        this.isTransitive = metadata.isTransitive() || metadata.isExternalVariant();
        this.selectedByVariantAwareResolution = selectedByVariantAwareResolution;
        this.moduleExclusions = resolveState.getModuleExclusions();
        this.dependenciesMayChange = component.getModule().isVirtualPlatform();
    }

    /**
     * Follow the chain of capability replacement results to determine the node, if any, that should
     * be targeted instead of this one. Otherwise, return this node if it has not been replaced.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    NodeState maybeResolveCapabilityReplacement() {
        NodeState node = this;
        while (node.capabilityReplacement != null) {
            NodeState winner = node.capabilityReplacement;
            // Validate that this replacement is still valid. If the winning node's component is no
            // longer selected, the target node is no longer present in the graph and the current node
            // should be returned. Note, that node may still end up conflicting with the corresponding
            // version-upgraded node that we originally conflicted with. We clear the stale pointer
            // rather than just skipping it, so that it cannot reactivate a superseded decision if the
            // evicted component is later re-selected.
            if (winner.getComponent() != winner.getComponent().getModule().getSelected()) {
                node.capabilityReplacement = null;
                break;
            }
            node = winner;
        }
        return node;
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

    @Override
    public ComponentState getComponent() {
        return component;
    }

    @Override
    public long getNodeId() {
        return nodeId;
    }

    @Override
    public VariantIdentifier getId() {
        return variantState.getMetadata().getId();
    }

    @Override
    public boolean isRoot() {
        return false;
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
    public VariantGraphResolveMetadata getMetadata() {
        return metadata;
    }

    @Override
    public VariantGraphResolveState getResolveState() {
        return variantState;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        if (variantState instanceof LocalVariantGraphResolveState) {
            return ((LocalVariantGraphResolveState) variantState).getFiles();
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return String.format("%s (%s)", component.getComponentId().getDisplayName(), metadata.getDisplayName());
    }

    public boolean isTransitive() {
        return isTransitive;
    }

    /**
     * Visits all dependencies that originate from this node, adding them as outgoing edges.
     * The {@link #outgoingEdges} collection is populated, as is the `discoveredEdges` parameter.
     * <p>
     * This method is incremental, and only adds edges to {@code discoveredEdges} that need to be
     * attached to target nodes, or that have selectors that have changed and therefore need to
     * go through selection again.
     *
     * @param discoveredEdges A collector for visited edges.
     */
    void visitOutgoingDependenciesAndCollectEdges(Collection<EdgeState> discoveredEdges) {
        ExcludeSpec resolutionFilter = computeModuleResolutionFilter(incomingEdges);
        List<EdgeState> filteredEdges = this.cachedFilteredEdges;
        StrictVersionConstraints ownStrictVersions = this.ownStrictVersions;
        if (filteredEdges == null || ownStrictVersions == null || !resolutionFilter.equals(this.cachedFilteredEdgesResolutionFilter)) {
            filteredEdges = updateFilteredEdges(resolutionFilter);
            ownStrictVersions = updateOwnStrictVersions(filteredEdges);
        }

        StrictVersionConstraints ancestorsStrictVersions = updateAncestorsStrictVersions(ownStrictVersions);
        if (ancestorsStrictVersions == null) {
            // None of our parents has known inheritable strict versions yet, and we are queued again once one does. Until then,
            // keep our edges as they are. Our own strict versions, which the nodes that endorse us need, are up to date.
            if (upcomingNoLongerPendingConstraints != null) {
                // We cannot link our constraints without knowing which of their versions to ignore. Their modules
                // must not wait for us. The constraints stay listed, and we link them once our strict versions are known.
                releaseDeferredSelections();
            }
            return;
        }

        doVisitDependencies(resolutionFilter, filteredEdges, ancestorsStrictVersions, discoveredEdges);

        assert (previousResolutionFilter == null) == (previousAncestorsStrictVersions == null);
        this.previousResolutionFilter = resolutionFilter;
        this.previousAncestorsStrictVersions = ancestorsStrictVersions;
    }

    private void doVisitDependencies(
        ExcludeSpec resolutionFilter,
        List<EdgeState> filteredEdges,
        StrictVersionConstraints ancestorsStrictVersions,
        Collection<EdgeState> discoveredEdges
    ) {
        boolean sameEdges;
        boolean ownerEdgesOnly = transitiveEdgeCount == 0 && !isRoot() && canIgnoreExternalVariant();
        if (ownerEdgesOnly) {
            assert !incomingEdges.isEmpty();
            // This node is part of the graph, but no incoming edges are transitive.
            // Act as if we have no declared dependencies. Remove any outgoing edges we may
            // have from a previous traversal. Virtual platform edges remain in order to
            // maintain version alignment (this behavior differs from non-virtual platform
            // edges, which is confusing and potentially not desired).

            // A visit of our dependencies also leaves constraints that are not edges, registered
            // with their pending modules, which must be removed.
            sameEdges = !visitedDependencies && hasOnlyOwnerEdgesCreatedWith(hasStrongOpinion());
        } else {
            // If we have visited our dependencies before, we can in some cases skip a complete visit.
            sameEdges = visitedDependencies
                && !virtualPlatformNeedsRefresh
                && filteredEdges.equals(this.previousFilteredEdges);
        }

        if (sameEdges) {
            if (!resolutionFilter.equals(previousResolutionFilter)) {
                // Our excludes changed. Update our outgoing edges with the new excludes.
                for (EdgeState outgoingEdge : outgoingEdges) {
                    outgoingEdge.updateTransitiveExcludesAndRequeueTargetNodes(resolutionFilter);
                }
            }

            if (!ancestorsStrictVersions.equals(previousAncestorsStrictVersions)) {
                // Our strict versions changed. Update our outgoing edges with the new strict versions.
                for (EdgeState outgoingEdge : outgoingEdges) {
                    outgoingEdge.recomputeSelectorAndRequeueTargetNodes(ancestorsStrictVersions, discoveredEdges);
                }
            }

            visitNewAndInvalidatedDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
            return;
        }

        // We are either doing a fresh visit, or we have some prior state from another visit.
        assert !visitedDependencies || previousResolutionFilter != null;

        // If we have any prior state, clear it before doing a full visit.
        removeOutgoingEdges();

        if (!ownerEdgesOnly) {
            visitDependencies(resolutionFilter, filteredEdges, ancestorsStrictVersions, discoveredEdges);
        }
        visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
    }

    /**
     * Whether our outgoing edges are exactly the edges to our virtual platforms, one per platform owner, created
     * as forced or not.
     */
    private boolean hasOnlyOwnerEdgesCreatedWith(boolean forced) {
        if (outgoingEdges.size() != component.getMetadata().getPlatformOwners().size()) {
            return false;
        }
        for (EdgeState outgoingEdge : outgoingEdges) {
            if (!(outgoingEdge.getDependencyMetadata() instanceof LenientPlatformDependencyMetadata md)
                || md.isConstraint()
                || md.isForce() != forced
            ) {
                return false;
            }
        }
        return true;
    }

    /**
     * Perform a partial visit of the dependencies of this node, only visiting new constraints
     * and edges that need to be recomputed.
     */
    private void visitNewAndInvalidatedDependencies(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        // Visit any constraints that were previously pending, but are no longer pending.
        if (upcomingNoLongerPendingConstraints != null && potentiallyActivatedConstraints != null) {
            for (ModuleIdentifier moduleId : upcomingNoLongerPendingConstraints) {
                Collection<EdgeState> edges = potentiallyActivatedConstraints.get(moduleId);
                if (!edges.isEmpty()) {
                    ModuleResolveState module = resolveState.getModule(moduleId);
                    if (module.isPending()) {
                        // The module went back to pending since we were notified that it was no longer pending.
                        module.getPendingDependencies().registerConstraintProvider(this);
                    } else {
                        for (EdgeState edge : edges) {
                            doLinkOutgoingEdge(edge, discoveredEdges, resolutionFilter, ancestorsStrictVersions, module, false);
                        }
                    }
                }
            }
            upcomingNoLongerPendingConstraints = null;
        }

        // Visit any other edges that were determined to need recomputation.
        if (edgesToRecompute != null) {
            discoveredEdges.addAll(edgesToRecompute);
            edgesToRecompute = null;
        }
    }

    private boolean canIgnoreExternalVariant() {
        if (!metadata.isExternalVariant()) {
            return true;
        }
        // We need to ignore external variants when all edges are artifact ones
        for (EdgeState incomingEdge : incomingEdges) {
            if (!incomingEdge.isArtifactOnlyEdge()) {
                return false;
            }
        }
        return true;
    }

    /*
     * When a node exits the graph, its constraints need to be cleaned up.
     * This means:
     * * Rescheduling any deferred selection impacted by a constraint coming from this node
     * * Making sure we no longer are registered as pending interest on nodes pointed by constraints
     */
    private void cleanupConstraints() {
        // This part covers constraint that were taken into account between a selection being deferred and this node being scheduled for traversal
        if (upcomingNoLongerPendingConstraints != null) {
            releaseDeferredSelections();
            upcomingNoLongerPendingConstraints = null;
        }
        // This part covers constraint that might be triggered in the future if the node they point gains a real edge
        if (previousFilteredEdges != null && !previousFilteredEdges.isEmpty()) {
            // We may have registered this node as pending if it had constraints.
            // Let's clear that state since it is no longer part of selection
            for (EdgeState edge : previousFilteredEdges) {
                if (edge.getDependencyMetadata().isConstraint()) {
                    ModuleResolveState targetModule = resolveState.getModule(edge.getTargetModuleIdentifier());
                    if (targetModule.isPending()) {
                        targetModule.unregisterConstraintProvider(this);
                    }
                }
            }
        }
    }

    /**
     * Triggers selection for the modules that deferred it until we link our constraints on them.
     * See {@link #prepareForConstraintNoLongerPending(ModuleIdentifier)}.
     */
    private void releaseDeferredSelections() {
        assert upcomingNoLongerPendingConstraints != null;
        for (ModuleIdentifier identifier : upcomingNoLongerPendingConstraints) {
            ModuleResolveState module = resolveState.getModule(identifier);
            for (EdgeState unattachedEdge : module.getUnattachedEdges()) {
                if (unattachedEdge.getSelector().requiresSelection()) {
                    // We have a selector that was deferred but the constraint has been removed in between
                    NodeState from = unattachedEdge.getFrom();
                    from.prepareToRecomputeEdge(unattachedEdge);
                }
            }
        }
    }

    void prepareToRecomputeEdge(EdgeState edgeToRecompute) {
        if (edgesToRecompute == null) {
            edgesToRecompute = new LinkedHashSet<>();
        }
        edgesToRecompute.add(edgeToRecompute);
        // Queue us only while our strict versions are known. Otherwise only a visit with known strict versions can
        // recompute the edge, and the change that makes them known queues us.
        if (ancestorsStrictVersions != null) {
            resolveState.onMoreSelected(this);
        }
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private void visitDependencies(
        ExcludeSpec resolutionFilter,
        List<EdgeState> filteredEdges,
        StrictVersionConstraints ancestorsStrictVersions,
        Collection<EdgeState> discoveredEdges
    ) {
        this.potentiallyActivatedConstraints = null;
        this.upcomingNoLongerPendingConstraints = null;

        for (EdgeState edge : filteredEdges) {
            registerOutgoingEdge(resolutionFilter, ancestorsStrictVersions, discoveredEdges, edge);
        }
        this.previousFilteredEdges = filteredEdges;

        // If there are 'pending' dependencies that share a target with any of these outgoing edges,
        // then reset the state of the node that owns those dependencies.
        // This way, all edges of the node will be re-processed.
        this.visitedDependencies = true;
    }

    private void registerOutgoingEdge(
        ExcludeSpec resolutionFilter,
        StrictVersionConstraints ancestorsStrictVersions,
        Collection<EdgeState> discoveredEdges,
        EdgeState dependencyEdge
    ) {
        boolean constraint = dependencyEdge.getDependencyMetadata().isConstraint();
        ModuleIdentifier moduleId = dependencyEdge.getTargetModuleIdentifier();
        ModuleResolveState module = resolveState.getModule(moduleId);

        boolean deferSelection = false;
        if (constraint) {
            registerActivatingConstraint(dependencyEdge, moduleId);
        } else {
            deferSelection = module.getPendingDependencies().addIncomingHardEdge();
        }

        if (constraint && module.isPending()) {
            // No hard dependency targeting this module. Remember this constraint for later in case we see a hard dependency later.
            module.registerConstraintProvider(this);
        } else {
            // We are a hard edge, or we are a constraint but there is already another hard edge targeting the same module.
            doLinkOutgoingEdge(dependencyEdge, discoveredEdges, resolutionFilter, ancestorsStrictVersions, module, deferSelection);
        }
    }

    private void registerActivatingConstraint(EdgeState edge, ModuleIdentifier targetModuleId) {
        if (potentiallyActivatedConstraints == null) {
            potentiallyActivatedConstraints = LinkedHashMultimap.create();
        }
        potentiallyActivatedConstraints.put(targetModuleId, edge);
    }

    private List<EdgeState> edges() {
        if (dependenciesMayChange || cachedEdges == null) {
            List<? extends DependencyMetadata> dependencies = getAllDependencies();
            if (transitiveEdgeCount == 0 && metadata.isExternalVariant()) {
                // there must be a single dependency state because this variant is an "available-at"
                // variant and here we are in the case the "including" component said that transitive
                // should be false so we need to arbitrarily carry that onto the dependency metadata
                assert dependencies.size() == 1;
                dependencies = Collections.singletonList(makeNonTransitive(dependencies.get(0)));
            }
            this.cachedEdges = cacheEdges(dependencies);
        }
        return cachedEdges;
    }

    protected List<? extends DependencyMetadata> getAllDependencies() {
        return variantState.getDependencies();
    }

    private static DependencyMetadata makeNonTransitive(DependencyMetadata dependencyMetadata) {
        return new NonTransitiveVariantDependencyMetadata(dependencyMetadata);
    }

    /**
     * Returns the edges which the given excludes do NOT exclude, and caches them for the visits that see the same excludes.
     */
    private List<EdgeState> updateFilteredEdges(ExcludeSpec spec) {
        List<EdgeState> filteredEdges = computeFilteredEdges(spec);
        this.cachedFilteredEdges = filteredEdges;
        this.cachedFilteredEdgesResolutionFilter = spec;
        return filteredEdges;
    }

    /**
     * Apply the given excludes to the list of edges, filtering out any edges
     * that are excluded.
     */
    private List<EdgeState> computeFilteredEdges(ExcludeSpec spec) {
        List<EdgeState> from = edges();
        if (from.isEmpty()) {
            return from;
        }
        List<EdgeState> tmp = new ArrayList<>(from.size());
        for (EdgeState edge : from) {
            if (!isExcluded(spec, edge)) {
                tmp.add(edge);
            }
        }
        return tmp;
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private List<EdgeState> cacheEdges(List<? extends DependencyMetadata> dependencies) {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }

        List<EdgeState> result = new ArrayList<>(dependencies.size());
        for (DependencyMetadata dependency : dependencies) {
            result.add(createEdge(dependency));
        }
        return result;
    }

    private EdgeState createEdge(DependencyMetadata dependency) {
        Try<SubstitutionResult> trySubstitution = resolveState.getDependencySubstitutionApplicator().applySubstitutions(
            dependency.getSelector(),
            dependency.getArtifacts()
        );

        if (!trySubstitution.isSuccessful()) {
            // Substitution failed
            ModuleVersionResolveException resolveFailure = new ModuleVersionResolveException(dependency.getSelector(), trySubstitution.getFailure().get());
            return new EdgeState(this, dependency, dependency.getSelector(), ImmutableList.of(), resolveFailure, resolveState);
        }

        // We performed substitution
        SubstitutionResult substitution = trySubstitution.get();
        DependencyMetadata updatedMetadata = metadataWithSubstitution(dependency, substitution);
        return new EdgeState(this, updatedMetadata, dependency.getSelector(), substitution.getRuleDescriptors(), null, resolveState);
    }

    private static DependencyMetadata metadataWithSubstitution(DependencyMetadata dependency, SubstitutionResult substitution) {
        ComponentSelector target = substitution.getTarget();
        ImmutableList<IvyArtifactName> artifacts = substitution.getArtifacts();
        if (target == null && artifacts == null) {
            return dependency;
        }

        ComponentSelector actualTarget = target != null ? target : dependency.getSelector();
        return artifacts == null
            ? dependency.withTarget(actualTarget)
            : dependency.withTargetAndArtifacts(actualTarget, artifacts);
    }

    private void doLinkOutgoingEdge(
        EdgeState dependencyEdge,
        Collection<EdgeState> discoveredEdges,
        ExcludeSpec resolutionFilter,
        StrictVersionConstraints ancestorsStrictVersions,
        ModuleResolveState module,
        boolean deferSelection
    ) {
        dependencyEdge.updateTransitiveExcludes(resolutionFilter);
        dependencyEdge.computeSelector(ancestorsStrictVersions, deferSelection);
        module.addUnattachedEdge(dependencyEdge);
        discoveredEdges.add(dependencyEdge);
        outgoingEdges.add(dependencyEdge);
    }

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     * Whether the platform is real or virtual is determined later during component resolution.
     *
     * @param resolutionFilter The excludes inherited from all incoming edges
     * @param ancestorsStrictVersions The strict versions inherited from all incoming edges
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        List<? extends VirtualComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            for (VirtualComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier platformId) {
                    // Register this module as a participant of the owning virtual platform.
                    // If the platform turns out to be real (published), this is harmless.
                    // If the platform is virtual, this enables the platform component to
                    // be resolved as a virtual platform later during metadata resolution.
                    ModuleResolveState platformModule = resolveState.getModule(platformId.getModuleIdentifier());
                    platformModule.getPlatformState().participatingModule(component.getModule());

                    boolean forced = hasStrongOpinion();
                    final ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(platformId.getModuleIdentifier(), platformId.getVersion());
                    DependencyMetadata dependencyMetadata = new LenientPlatformDependencyMetadata(selector, platformId, forced, false);
                    EdgeState virtualPlatformEdge = createEdge(dependencyMetadata);

                    registerOutgoingEdge(
                        resolutionFilter,
                        ancestorsStrictVersions,
                        discoveredEdges,
                        virtualPlatformEdge
                    );
                } else {
                    throw new IllegalStateException("Expected platform ID to be a module identifier: " + owner);
                }
            }
        }
    }

    private boolean hasStrongOpinion() {
        for (EdgeState edgeState : incomingEdges) {
            if (edgeState.getSelector().hasStrongOpinion()) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(ExcludeSpec excludeSpec, EdgeState edgeState) {
        DependencyMetadata dependency = edgeState.getDependencyMetadata();
        if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
            LOGGER.debug("{} is filtered.", dependency);
            return true;
        }
        if (excludeSpec == moduleExclusions.nothing()) {
            return false;
        }

        ModuleIdentifier targetModuleId = edgeState.getTargetModuleIdentifier();

        if (excludeSpec.excludes(targetModuleId)) {
            LOGGER.debug("{} is excluded from {} by {}.", targetModuleId, this, excludeSpec);
            return true;
        }

        // If we were substituted, apply the exclusion to the original selector as well.
        ComponentSelector requestedSelector = edgeState.getRequested();
        if (requestedSelector != edgeState.getDependencyMetadata().getSelector()) {
            return excludeSpec.excludes(resolveState.getComponentSelectorConverter().getModuleVersionId(requestedSelector).getModule());
        }

        return false;
    }

    void addIncomingEdge(EdgeState dependencyEdge) {
        if (!incomingEdges.contains(dependencyEdge)) {
            cachedModuleResolutionFilter = null;
            incomingEdges.add(dependencyEdge);
            incomingHash += dependencyEdge.hashCode();
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount++;
            }
            requeueEndorsingParent(dependencyEdge);
            resolveState.onMoreSelected(this);
        }
    }

    void removeIncomingEdge(EdgeState dependencyEdge) {
        if (incomingEdges.remove(dependencyEdge)) {
            cachedModuleResolutionFilter = null;
            incomingHash -= dependencyEdge.hashCode();
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount--;
            }
            requeueEndorsingParent(dependencyEdge);
            resolveState.resetStrictVersions(this);
            resolveState.onFewerSelected(this);
        }
    }

    /**
     * Removes all incoming edges targeting this node. This is faster than individually
     * calling {@link #removeIncomingEdge(EdgeState)} for each incoming edge.
     *
     * @return All removed incoming edges.
     */
    List<EdgeState> removeAllIncomingEdges() {
        if (incomingEdges.isEmpty()) {
            return Collections.emptyList();
        }

        List<EdgeState> removedEdges = ImmutableList.copyOf(incomingEdges);
        incomingEdges.clear();
        cachedModuleResolutionFilter = null;
        incomingHash = 0;
        transitiveEdgeCount = 0;

        for (EdgeState incomingEdge : removedEdges) {
            requeueEndorsingParent(incomingEdge);
        }
        resolveState.resetStrictVersions(this);
        resolveState.onFewerSelected(this);

        return removedEdges;
    }

    /**
     * Must be called whenever an incoming edge that endorses our strict versions is added or removed,
     * to queue the source node so it can recompute its endorsed and inheritable strict versions.
     */
    private void requeueEndorsingParent(EdgeState incomingEdge) {
        if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
            resolveState.onMoreSelected(incomingEdge.getFrom());
        }
    }

    void clearTransitiveExclusionsAndEnqueue() {
        cachedModuleResolutionFilter = null;
        // TODO: We can eagerly compute the exclusions and enqueue only on change
        resolveState.onMoreSelected(this);
    }

    /**
     * Determine if this node should be processed when it is dequeued during traversal, or if its
     * subgraph should be removed from the graph.
     * <p>
     * True if this node has incoming edges and is not in a conflict. We temporarily delay building
     * subgraphs of nodes in conflict while the conflict has not yet been resolved to avoid subgraphs
     * of losing nodes from affecting the graph shape.
     */
    boolean shouldBuildSubgraph() {
        return isSelected() &&
            !isInCapabilityConflict() &&
            // We need special handling for root since it does not yet have
            // its own module, but should never be considered a conflict participant.
            !(getComponent().getModule().isInModuleConflict() && !isRoot());
    }

    @Override
    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    public void markInCapabilityConflict() {
        this.numCapabilityConflicts++;
        if (numCapabilityConflicts == 1) {
            resolveState.onFewerSelected(this);
        }
    }

    public boolean isInCapabilityConflict() {
        return numCapabilityConflicts > 0;
    }

    public void onFilteredFromConflict() {
        numCapabilityConflicts--;
        assert numCapabilityConflicts >= 0;
    }

    /**
     * Resolve a capability conflict this node is involved in.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public void resolveCapabilityConflict(NodeState winner) {
        numCapabilityConflicts--;
        assert numCapabilityConflicts >= 0;
        if (winner == this) {
            resolveState.onMoreSelected(this);
        } else {
            this.capabilityReplacement = winner;
            restartIncomingEdges();
        }
    }

    public boolean isRejectedForCapabilityConflict() {
        return capabilityReject != null;
    }

    public void rejectForCapabilityConflict(Capability capability, Collection<NodeState> conflictedNodes) {
        if (this.capabilityReject == null) {
            this.capabilityReject = Pair.of(capability, new HashSet<>(conflictedNodes));
        } else {
            mergeCapabilityRejects(capability, conflictedNodes);
        }

        numCapabilityConflicts--;
        assert numCapabilityConflicts >= 0;
        resolveState.onMoreSelected(this);
    }

    private void mergeCapabilityRejects(Capability capability, Collection<NodeState> conflictedNodes) {
        // Only merge if about the same capability, otherwise last wins
        if (this.capabilityReject.getLeft().equals(capability)) {
            this.capabilityReject.getRight().addAll(conflictedNodes);
        } else {
            this.capabilityReject = Pair.of(capability, new HashSet<>(conflictedNodes));
        }
    }

    public String getRejectedErrorMessage() {
        assert capabilityReject != null;
        return formatCapabilityRejectMessage(getComponent().getModule().getId(), capabilityReject);
    }

    @SuppressWarnings("DataFlowIssue") // Conflict nodes are not null when this is called
    private static String formatCapabilityRejectMessage(ModuleIdentifier id, Pair<Capability, Collection<NodeState>> capabilityConflict) {
        var conflictNodes = capabilityConflict.right;
        var multipleConflicts = conflictNodes.size() > 1;
        var conflictMsg = multipleConflicts ? "conflicts" : "conflict";
        var providedByMsg = multipleConflicts ? conflictNodes.stream().map(NodeState::getDisplayName).sorted().toList().toString() : conflictNodes.iterator().next().getDisplayName();
        var groupNameMsg = multipleConflicts ? "All" : "Both";
        return "Module '" + id + "' has been rejected:\n" +
            "   Cannot select module because of " + conflictMsg + " with " + providedByMsg + ". " + groupNameMsg + " provide capability '" + formatCapability(capabilityConflict.left) + "'.";
    }

    private static String formatCapability(Capability capability) {
        return capability.getGroup() + ":" + capability.getName() + ":" + capability.getVersion();
    }

    private ExcludeSpec computeModuleResolutionFilter(List<EdgeState> incomingEdges) {
        if (metadata.isExternalVariant()) {
            // If the current node represents an external variant, we must not consider its excludes
            // because it's some form of "delegation"
            return moduleExclusions.excludeAny(
                incomingEdges.stream()
                    .map(EdgeState::getTransitiveExclusions)
                    .filter(Objects::nonNull)
                    .collect(PersistentSet.toPersistentSet())
            );
        }
        if (incomingEdges.size() == 1) {
            // At the same time if the current node _comes from_ a delegated variant (available-at)
            // then we need to take the exclusion filter from the origin node instead
            NodeState from = incomingEdges.get(0).getFrom();
            if (from.getMetadata().isExternalVariant()) {
                return computeModuleResolutionFilter(from.getIncomingEdges());
            }
        }
        ExcludeSpec nodeExclusions = computeNodeExclusions();
        if (incomingEdges.isEmpty()) {
            return nodeExclusions;
        }

        return computeExclusionFilter(incomingEdges, nodeExclusions);
    }

    private ExcludeSpec computeNodeExclusions() {
        if (cachedNodeExclusions == null) {
            cachedNodeExclusions = moduleExclusions.excludeAny(variantState.getExcludes());
        }
        return cachedNodeExclusions;
    }

    private ExcludeSpec computeExclusionFilter(List<EdgeState> incomingEdges, ExcludeSpec nodeExclusions) {
        int incomingEdgeCount = incomingEdges.size();
        if (sameIncomingEdgesAsPreviousPass(incomingEdgeCount)) {
            // if we reach this point it means the node selection was restarted, but
            // effectively it has the same incoming edges as before, so we can return
            // the result we computed last time
            return cachedModuleResolutionFilter;
        }
        if (incomingEdgeCount == 1) {
            return computeExclusionFilterSingleIncomingEdge(incomingEdges.get(0), nodeExclusions);
        }
        return computeModuleExclusionsManyEdges(incomingEdges, nodeExclusions, incomingEdgeCount);
    }

    private ExcludeSpec computeModuleExclusionsManyEdges(List<EdgeState> incomingEdges, ExcludeSpec nodeExclusions, int incomingEdgeCount) {
        ExcludeSpec nothing = moduleExclusions.nothing();
        ExcludeSpec edgeExclusions = null;
        PersistentSet<ExcludeSpec> excludedByBoth = PersistentSet.of();
        PersistentSet<ExcludeSpec> excludedByEither = PersistentSet.of();
        for (EdgeState dependencyEdge : incomingEdges) {
            if (dependencyEdge.isTransitive()) {
                if (edgeExclusions != nothing) {
                    // Transitive dependency
                    ExcludeSpec exclusions = dependencyEdge.getExclusions();
                    if (edgeExclusions == null || exclusions == nothing) {
                        edgeExclusions = exclusions;
                    } else if (edgeExclusions != exclusions) {
                        excludedByBoth = excludedByBoth.plus(exclusions);
                    }
                    if (edgeExclusions == nothing) {
                        // if exclusions == nothing, then the intersection will be "nothing"
                        excludedByBoth = PersistentSet.of();
                    }
                }
            } else if (dependencyEdge.isConstraint()) {
                excludedByEither = collectEdgeConstraint(nodeExclusions, excludedByEither, dependencyEdge, nothing);
            }
        }
        edgeExclusions = intersectEdgeExclusions(edgeExclusions, excludedByBoth);
        nodeExclusions = joinNodeExclusions(nodeExclusions, excludedByEither);
        return joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions, edgeExclusions, incomingEdgeCount);
    }

    private ExcludeSpec computeExclusionFilterSingleIncomingEdge(EdgeState dependencyEdge, ExcludeSpec nodeExclusions) {
        ExcludeSpec exclusions = null;
        if (dependencyEdge.isTransitive()) {
            exclusions = dependencyEdge.getExclusions();
        } else if (dependencyEdge.isConstraint()) {
            exclusions = dependencyEdge.getEdgeExclusions();
        }
        if (exclusions == null) {
            exclusions = moduleExclusions.nothing();
        }
        return joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions, exclusions, 1);
    }

    private ExcludeSpec joinEdgeAndNodeExclusionsThenCacheResult(ExcludeSpec nodeExclusions, ExcludeSpec edgeExclusions, int incomingEdgeCount) {
        ExcludeSpec result = moduleExclusions.excludeAny(edgeExclusions, nodeExclusions);
        // We use a set here because for excludes, order of edges is irrelevant
        // so we hit the cache more by using a set
        previousIncomingEdgeCount = incomingEdgeCount;
        previousIncomingHash = incomingHash;
        cachedModuleResolutionFilter = result;
        return result;
    }

    private static PersistentSet<ExcludeSpec> collectEdgeConstraint(ExcludeSpec nodeExclusions, PersistentSet<ExcludeSpec> excludedByEither, EdgeState dependencyEdge, ExcludeSpec nothing) {
        // Constraint: only consider explicit exclusions declared for this constraint
        ExcludeSpec constraintExclusions = dependencyEdge.getEdgeExclusions();
        if (constraintExclusions != nothing && constraintExclusions != nodeExclusions) {
            return excludedByEither.plus(constraintExclusions);
        }
        return excludedByEither;
    }

    @Nullable
    private ExcludeSpec joinNodeExclusions(@Nullable ExcludeSpec nodeExclusions, PersistentSet<ExcludeSpec> excludedByEither) {
        if (excludedByEither.isNotEmpty() && nodeExclusions != null) {
            return moduleExclusions.excludeAny(
                excludedByEither.plus(nodeExclusions)
            );
        }
        return nodeExclusions;
    }

    @Nullable
    private ExcludeSpec intersectEdgeExclusions(@Nullable ExcludeSpec edgeExclusions, PersistentSet<ExcludeSpec> excludedByBoth) {
        if (edgeExclusions == moduleExclusions.nothing()) {
            return edgeExclusions;
        }
        if (excludedByBoth.isNotEmpty()) {
            return moduleExclusions.excludeAll(
                edgeExclusions != null
                    ? excludedByBoth.plus(edgeExclusions)
                    : excludedByBoth
            );
        }
        return edgeExclusions;
    }

    private static StrictVersionConstraints collectStrictVersions(List<EdgeState> edges) {
        PersistentSet<ModuleIdentifier> constraintsSet = PersistentSet.of();
        for (EdgeState edge : edges) {
            if (edge.getDependencyMetadata().getSelector() instanceof ModuleComponentSelector mcs &&
                !StringUtils.isEmpty(mcs.getVersionConstraint().getStrictVersion())
            ) {
                constraintsSet = constraintsSet.plus(mcs.getModuleIdentifier());
            }
        }
        return StrictVersionConstraints.of(constraintsSet);
    }

    /**
     * Updates our own strict versions to the ones the given edges declare, and queues the nodes that
     * endorse us if they changed.
     */
    private StrictVersionConstraints updateOwnStrictVersions(List<EdgeState> filteredEdges) {
        StrictVersionConstraints newStrictVersions = collectStrictVersions(filteredEdges);
        StrictVersionConstraints existingOwnStrictVersions = this.ownStrictVersions;
        if (Objects.equals(existingOwnStrictVersions, newStrictVersions)) {
            return existingOwnStrictVersions;
        }
        this.ownStrictVersions = newStrictVersions;

        boolean endorsed = false;
        for (EdgeState incomingEdge : incomingEdges) {
            if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
                endorsed = true;
                // Nodes that endorse us include them in their inheritable strict versions.
                resolveState.onMoreSelected(incomingEdge.getFrom());
            }
        }

        if (endorsed) {
            // The edges that endorse us pass on the strict versions they endorse, minus our own. With fewer own strict
            // versions, we may inherit more, so our ancestors strict versions can grow, which needs a reset. With more, or
            // when our own strict versions are first known, they can only shrink.
            if (existingOwnStrictVersions != null && !existingOwnStrictVersions.minus(newStrictVersions).isEmpty()) {
                resolveState.resetStrictVersions(this);
            } else {
                // Queue us now, so we are visited right after the nodes that endorse us rather than whenever they queue us.
                resolveState.onMoreSelected(this);
            }
        }
        return newStrictVersions;
    }

    /**
     * Completes the visit of this node, once the edges it discovered are attached. Recomputes the strict versions
     * it endorses and its inheritable strict versions, and queues its children if those changed.
     */
    void updateInheritableStrictVersions() {
        if (ancestorsStrictVersions != null) {
            this.endorsedStrictVersions = sameIfEqual(endorsedStrictVersions, computeEndorsedStrictVersions(outgoingEdges));
        }
        StrictVersionConstraints inheritable = computeInheritableStrictVersions(endorsedStrictVersions);
        StrictVersionConstraints previous = this.inheritableStrictVersions;
        // When our inheritable strict versions shrink or become known, the children only need to recompute
        // their value. When they grow or become unknown, values the children derived from ours may be too
        // small, in a cycle in particular, so they are reset.
        if (!Objects.equals(previous, inheritable)) {
            boolean grew = inheritable == null || (previous != null && !inheritable.minus(previous).isEmpty());
            for (EdgeState outgoingEdge : outgoingEdges) {
                for (NodeState targetNode : outgoingEdge.getTargetNodes()) {
                    if (grew) {
                        resolveState.resetStrictVersions(targetNode);
                    } else {
                        resolveState.onMoreSelected(targetNode);
                    }
                }
            }
            this.inheritableStrictVersions = inheritable;
        }
    }

    /**
     * Determine all strict versions endorsed by this node, which are the own strict versions of the targets of our
     * endorsing edges. Returns null while a target has not computed them yet, which it does at its next visit.
     * An edge without a target yet endorses nothing.
     */
    private static @Nullable StrictVersionConstraints computeEndorsedStrictVersions(List<EdgeState> outgoingEdges) {
        StrictVersionConstraints endorsedStrictVersions = StrictVersionConstraints.EMPTY;
        for (EdgeState edgeState : outgoingEdges) {
            if (edgeState.getDependencyMetadata().isEndorsingStrictVersions()) {
                // Do not wait for an unattached edge, as it may not be attached while we wait. Until then we pass on
                // values that can be too small, and accept the resets that result. Attaching it queues us.
                for (NodeState endorsedNode : edgeState.getTargetNodes()) {
                    if (endorsedNode.ownStrictVersions == null) {
                        // Wait until the target is visited, rather than pass on values that are too small. It computes its
                        // own strict versions without waiting, and queues us then.
                        return null;
                    }
                    endorsedStrictVersions = endorsedStrictVersions.union(endorsedNode.ownStrictVersions);
                }
            }
        }
        return endorsedStrictVersions;
    }

    /**
     * Returns the previous value if it is equal to the new one, so that equal values stay the same instance.
     * Strict versions are compared and combined at every visit, which is much cheaper for the same instance.
     */
    private static @Nullable StrictVersionConstraints sameIfEqual(@Nullable StrictVersionConstraints previous, @Nullable StrictVersionConstraints value) {
        return previous != null && previous.equals(value) ? previous : value;
    }

    /**
     * Updates the strict versions we inherit from our parents, given our own strict versions. Returns them, or null
     * while none of our parents has known inheritable strict versions.
     */
    private @Nullable StrictVersionConstraints updateAncestorsStrictVersions(StrictVersionConstraints ownStrictVersions) {
        StrictVersionConstraints ancestorsStrictVersions = sameIfEqual(
            this.ancestorsStrictVersions != null ? this.ancestorsStrictVersions : previousAncestorsStrictVersions,
            collectAncestorsStrictVersions(ownStrictVersions)
        );
        this.ancestorsStrictVersions = ancestorsStrictVersions;
        return ancestorsStrictVersions;
    }

    /**
     * Determines all strict versions inherited from ancestors. When a node declares strict
     * versions, either through its own dependencies, or by endorsement, those strict versions apply
     * to all descendants of that node's exclusive subgraph. If a given node belongs to multiple
     * subgraphs, a strict version is only inherited if all parent subgraphs provide a
     * strict version for that module. For this reason, we compute the intersection of strict
     * versions coming from all incoming edges. Returns null if none of them has a known value yet.
     * <p>
     * Takes our own strict versions, which the edges that endorse us leave out of what they pass on.
     */
    @VisibleForTesting
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    @Nullable StrictVersionConstraints collectAncestorsStrictVersions(StrictVersionConstraints ownStrictVersions) {
        if (isRoot()) {
            // In Gradle 10 it will be forbidden for edges to target the root, and this can check for no incoming edges instead.
            return StrictVersionConstraints.EMPTY;
        }

        if (incomingEdges.size() == 1) {
            EdgeState dependencyEdge = incomingEdges.get(0);
            return getStrictVersionsForEdge(dependencyEdge, ownStrictVersions);
        }

        StrictVersionConstraints ancestorsStrictVersions = null;
        for (EdgeState dependencyEdge : incomingEdges) {
            StrictVersionConstraints edgeStrictVersions = getStrictVersionsForEdge(dependencyEdge, ownStrictVersions);
            // If the strict versions of this edge are unknown, its source queues us once they are known. Until then, leaving the
            // edge out can only make our value larger than the final one, which our next visit shrinks. Waiting for every edge
            // instead would never end in a cycle, where the source depends on our value.
            if (edgeStrictVersions != null) {
                ancestorsStrictVersions = ancestorsStrictVersions == null
                    ? edgeStrictVersions
                    : ancestorsStrictVersions.intersect(edgeStrictVersions);

                if (ancestorsStrictVersions == StrictVersionConstraints.EMPTY) {
                    // No need to continue. Empty intersected with anything is empty.
                    return StrictVersionConstraints.EMPTY;
                }
            }
        }
        return ancestorsStrictVersions;
    }

    /**
     * Determine the strict versions inherited through a given edge, or null if not known yet. These are the
     * inheritable strict versions of its source, computed again without our own strict versions if the edge endorses us.
     */
    private static @Nullable StrictVersionConstraints getStrictVersionsForEdge(EdgeState dependencyEdge, StrictVersionConstraints ownStrictVersions) {
        NodeState from = dependencyEdge.getFrom();
        if (!dependencyEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
            return from.inheritableStrictVersions;
        }

        // If the source node endorses us, then we might be the source of a strict version that it
        // endorses. For this reason, we inherit a parent's endorsed strict versions only if we may
        // not be the source of that strict version.
        StrictVersionConstraints parentEndorsedStrictVersions = from.endorsedStrictVersions;
        if (parentEndorsedStrictVersions == null) {
            return null;
        }
        return from.computeInheritableStrictVersions(parentEndorsedStrictVersions.minus(ownStrictVersions));
    }

    /**
     * The strict versions children can inherit from this node, with the given endorsed strict versions,
     * or null if not known yet.
     */
    private @Nullable StrictVersionConstraints computeInheritableStrictVersions(@Nullable StrictVersionConstraints endorsedStrictVersions) {
        if (ownStrictVersions == null || ancestorsStrictVersions == null || endorsedStrictVersions == null) {
            return null;
        }
        StrictVersionConstraints strongStrictVersions = ownStrictVersions.union(ancestorsStrictVersions);
        return strongStrictVersions.union(endorsedStrictVersions);
    }

    /**
     * Forgets the strict versions this node inherits, after a change that may make them larger. The node is queued
     * to compute them again at its next visit, and its children are queued to be reset too, as their values may
     * derive from its value.
     */
    void resetAncestorsStrictVersions() {
        if (isRoot()) {
            // In Gradle 10 it will be forbidden for edges to target the root, and we can remove this condition.
            return;
        }
        if (this.ancestorsStrictVersions == null) {
            return;
        }
        this.ancestorsStrictVersions = null;
        this.inheritableStrictVersions = null;
        resolveState.onMoreSelected(this);
        for (EdgeState outgoingEdge : outgoingEdges) {
            for (NodeState targetNode : outgoingEdge.getTargetNodes()) {
                resolveState.resetStrictVersions(targetNode);
            }
        }
    }

    boolean hasKnownAncestorsStrictVersions() {
        return ancestorsStrictVersions != null;
    }

    private boolean sameIncomingEdgesAsPreviousPass(int incomingEdgeCount) {
        // This is a heuristic, more than truth: it is possible that the 2 long hashs
        // are identical AND that the sizes of collections are identical, but it's
        // extremely unlikely (never happened on test cases even on large dependency graph)
        return cachedModuleResolutionFilter != null
            && previousIncomingHash == incomingHash
            && previousIncomingEdgeCount == incomingEdgeCount;
    }

    /**
     * This method is effectively the inverse of {@link #visitOutgoingDependenciesAndCollectEdges(Collection)}.
     * <p>
     * Cleans up the outgoing state of this node, undoing any effects this node has on the graph.
     * To be called when this node is removed from the graph.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public void removeOutgoingEdges() {
        if (previousResolutionFilter == null) {
            return;
        }

        if (!outgoingEdges.isEmpty()) {
            for (EdgeState outgoingEdge : outgoingEdges) {
                disconnectOutgoingEdge(outgoingEdge);
            }
            outgoingEdges.clear();
        }
        cleanupConstraints();
        previousResolutionFilter = null;
        previousAncestorsStrictVersions = null;
        visitedDependencies = false;
        previousFilteredEdges = null;
        edgesToRecompute = null;
        virtualPlatformNeedsRefresh = false;
    }

    private void disconnectOutgoingEdge(EdgeState outgoingEdge) {
        outgoingEdge.detachFromTargetNodes();
        outgoingEdge.getSelector().getTargetModule().disconnectIncomingEdge(this, outgoingEdge);
    }

    /**
     * Retarget all incoming edges of this node. Called in two contexts:
     * <ul>
     *     <li>On losing nodes of capability conflicts, to move their edges to the winner.</li>
     *     <li>On nodes (and their capability replacements) of components losing version or module conflicts
     *         in {@link ModuleResolveState#changeSelection}, to retarget edges to the new selection.</li>
     * </ul>
     * In the second case, the node may be a capability conflict winner that has its own
     * legitimate incoming edges from other modules. Those edges re-attach to this node after
     * retargeting, so the node may still have incoming edges when this method returns.
     */
    void restartIncomingEdges() {
        if (incomingEdges.size() == 1) {
            retargetSingleEdge(incomingEdges.get(0));
        } else if (incomingEdges.size() > 1) {
            for (EdgeState edge : new ArrayList<>(incomingEdges)) {
                retargetSingleEdge(edge);
            }
        }
    }

    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private void retargetSingleEdge(EdgeState edge) {
        edge.retarget();
        // The edge should have retargeted away from this node. If it still targets us, we must be
        // the winner of a capability conflict, and the edge either targets us natively or was
        // redirected to us via a loser's still-valid capability replacement. In both cases, our
        // component must still be its module's selection.
        assert !edge.getTargetNodes().contains(this) || getComponent() == getComponent().getModule().getSelected();
    }

    void prepareForConstraintNoLongerPending(ModuleIdentifier moduleIdentifier) {
        if (upcomingNoLongerPendingConstraints == null) {
            upcomingNoLongerPendingConstraints = new LinkedHashSet<>();
        }
        upcomingNoLongerPendingConstraints.add(moduleIdentifier);
        // Trigger a replay on this node, to add new constraints to graph
        resolveState.onFewerSelected(this);
    }

    void markForVirtualPlatformRefresh() {
        assert component.getModule().isVirtualPlatform();
        virtualPlatformNeedsRefresh = true;
        this.cachedFilteredEdges = null;
        resolveState.onFewerSelected(this);
    }

    void removeOutgoingEdge(EdgeState edge) {
        outgoingEdges.remove(edge);
        edge.clearSelector();
    }

    /**
     * Determine if this node provides a capability with the given group and name.
     * If so, return it. Otherwise, return null.
     */
    public @Nullable ImmutableCapability findCapability(String group, String name) {
        ImmutableCapabilities capabilities = metadata.getCapabilities();
        if (capabilities.isEmpty()) {
            // No capabilities declared. Use the component's implicit capability.
            if (component.getId().getGroup().equals(group) && component.getId().getName().equals(name)) {
                return component.getImplicitCapability();
            }
        } else {
            for (ImmutableCapability capability : capabilities) {
                if (capability.getGroup().equals(group) && capability.getName().equals(name)) {
                    return capability;
                }
            }
        }
        return null;
    }

    public boolean isAttachedToVirtualPlatform() {
        for (EdgeState incomingEdge : incomingEdges) {
            if (incomingEdge.getDependencyMetadata() instanceof LenientPlatformDependencyMetadata) {
                return true;
            }
        }
        return false;
    }

    boolean hasShadowedCapability() {
        for (Capability capability : metadata.getCapabilities().asSet()) {
            if (capability instanceof ShadowedCapability) {
                return true;
            }
        }
        return false;
    }

    boolean isSelectedByVariantAwareResolution() {
        // the order is strange logically but here for performance optimization
        return selectedByVariantAwareResolution && isSelected();
    }

    @Nullable
    @Override
    public ResolvedGraphVariant getExternalVariant() {
        if (canIgnoreExternalVariant()) {
            return null;
        }
        if (findingExternalVariants) {
            // There is a cycle in the external variants
            LOGGER.warn("Detecting cycle in external variants for :\n" + computePathToRoot());
            findingExternalVariants = false;
            return null;
        }
        findingExternalVariants = true;
        // An external variant must have exactly one outgoing edge
        // corresponding to the dependency to the external module
        // can be 0 if the selected variant also happens to be excluded
        // for example via configuration excludes
        assert outgoingEdges.size() <= 1;
        try {
            for (EdgeState outgoingEdge : outgoingEdges) {
                //noinspection ConstantConditions
                return outgoingEdge.getFirstTargetNode();
            }
            return null;
        } finally {
            findingExternalVariants = false;
        }
    }

    private String computePathToRoot() {
        TreeFormatter formatter = new TreeFormatter();
        formatter.node(getDisplayName());
        NodeState from = this;
        int depth = 0;
        do {
            from = getFromNode(from);
            if (from != null) {
                formatter.startChildren();
                formatter.node(getDisplayName());
                depth++;
            }
        } while (from != null && !(from instanceof RootNode));
        for (int i = 0; i < depth; i++) {
            formatter.endChildren();
        }
        formatter.node("Dependency resolution has ignored the cycle to produce a result. It is recommended to resolve the cycle by upgrading one or more dependencies.");
        return formatter.toString();
    }

    @Nullable
    private NodeState getFromNode(NodeState from) {
        List<EdgeState> incomingEdges = from.getIncomingEdges();
        if (incomingEdges.isEmpty()) {
            return null;
        }
        return incomingEdges.get(0).getFrom();
    }

    public Set<NodeState> getReachableNodes() {
        Set<NodeState> visited = new HashSet<>();
        dependsTransitivelyOn(visited);
        return visited;
    }

    private void dependsTransitivelyOn(Set<NodeState> visited) {
        for (EdgeState outgoingEdge : getOutgoingEdges()) {
            for (NodeState nodeState : outgoingEdge.getTargetNodes()) {
                if (visited.add(nodeState)) {
                    nodeState.dependsTransitivelyOn(visited);
                }
            }
        }
    }

    private static class NonTransitiveVariantDependencyMetadata extends DelegatingDependencyMetadata {
        private final DependencyMetadata dependencyMetadata;

        public NonTransitiveVariantDependencyMetadata(DependencyMetadata dependencyMetadata) {
            super(dependencyMetadata);
            this.dependencyMetadata = dependencyMetadata;
        }

        @Override
        public DependencyMetadata withTarget(ComponentSelector target) {
            return makeNonTransitive(dependencyMetadata.withTarget(target));
        }

        @Override
        public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, ImmutableList<IvyArtifactName> artifacts) {
            return makeNonTransitive(dependencyMetadata.withTargetAndArtifacts(target, artifacts));
        }

        @Override
        public boolean isTransitive() {
            return false;
        }

        @Override
        public DependencyMetadata withReason(String reason) {
            return makeNonTransitive(dependencyMetadata.withReason(reason));
        }

        @Override
        public String toString() {
            return "Non transitive dependency for external variant " + dependencyMetadata;
        }
    }
}
