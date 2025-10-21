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
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.component.model.VariantIdentifier;
import org.gradle.internal.logging.text.TreeFormatter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Nullable
    ExcludeSpec previousTraversalExclusions;

    // In opposite to outgoing edges, virtual edges are for now pretty rare, so they are created lazily
    private @Nullable List<EdgeState> virtualEdges;
    private boolean queued;
    private boolean evicted;
    private int transitiveEdgeCount;
    private @Nullable Set<ModuleIdentifier> upcomingNoLongerPendingConstraints;

    /**
     * Virtual platforms require their constraints to be recomputed each time, as each module addition
     * can cause a shift in versions. Therefore, if this true, we perform a full dependency visit even
     * though we've already visited this node's dependencies before.
     */
    private boolean virtualPlatformNeedsRefresh;
    private @Nullable Set<EdgeState> edgesToRecompute;
    private @Nullable Multimap<ModuleIdentifier, DependencyState> potentiallyActivatedConstraints;

    // caches
    private final Map<DependencyMetadata, DependencyState> dependencyStateCache = new HashMap<>();
    private final Map<DependencyState, EdgeState> edgesCache = new HashMap<>();

    // Caches the list of dependency states for dependencies
    private @Nullable List<DependencyState> cachedDependencyStates;

    // Caches the list of dependency states which are NOT excluded
    private @Nullable List<DependencyState> cachedFilteredDependencyStates;

    // exclusions optimizations
    private @Nullable ExcludeSpec cachedNodeExclusions;
    private int previousIncomingEdgeCount;
    private long previousIncomingHash;
    private long incomingHash;
    private @Nullable ExcludeSpec cachedModuleResolutionFilter;

    /**
     * False if a full visit of dependencies of this node must be performed during
     * {@link #visitOutgoingDependenciesAndCollectEdges(Collection)}. This field ensures
     * we remember whether we short-circuited a dependency visit, and therefore skipped
     * linking edge state and other state updates.
     */
    private boolean visitedDependencies = false;

    /**
     * The transitive strict versions from inherited from parents, from the previous
     * graph traversal.
     */
    private @Nullable StrictVersionConstraints previousAncestorsStrictVersions;

    /**
     * Our own strict version constraints, from the previous graph traversal.
     */
    private @Nullable StrictVersionConstraints ownStrictVersions;

    /**
     * Cached copy of all endorsed strict versions. Must be invalidated whenever
     * an outgoing endorsing edge is added or removed, or if the target endorsed
     * node's own strict versions change.
     */
    private @Nullable StrictVersionConstraints cachedEndorsedStrictVersions;

    private boolean removingOutgoingEdges;
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
    public ComponentGraphResolveState getComponentResolveState() {
        return getComponent().getResolveState();
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

    public String getDisplayName() {
        return String.format("'%s' (%s)", component.getComponentId().getDisplayName(), metadata.getDisplayName());
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
        StrictVersionConstraints ancestorsStrictVersions = collectAncestorsStrictVersions();

        doVisitDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges);

        assert (previousTraversalExclusions == null) == (previousAncestorsStrictVersions == null);
        this.previousTraversalExclusions = resolutionFilter;
        this.previousAncestorsStrictVersions = ancestorsStrictVersions;
    }

    private void doVisitDependencies(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        // If none of the incoming edges are transitive, act as if we have no declared dependencies
        // and only visit virtual edges to platform owners. If we have any edges from previous traversals,
        // clear that state.
        if (transitiveEdgeCount == 0 && !isRoot() && canIgnoreExternalVariant()) {
            cleanupConstraints();
            if (previousTraversalExclusions != null) {
                removeOutgoingEdges();
            }
            if (!incomingEdges.isEmpty()) {
                if (this.ownStrictVersions == null) {
                    collectOwnStrictVersions(resolutionFilter);
                }
                visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
            }
            return;
        }

        // If we have visited our dependencies before, we can in some cases skip a complete visit.
        boolean sameExcludes = resolutionFilter.equals(previousTraversalExclusions);
        if (visitedDependencies
            && !virtualPlatformNeedsRefresh
            && (sameExcludes || applyDependencyExcludes(resolutionFilter, dependencyStates()).equals(this.cachedFilteredDependencyStates))
        ) {
            // Our excludes did not change, or after applying new excludes to our outgoing dependencies,
            // the filtered dependencies did not change. We have the same dependencies as the previous traversal.

            if (!sameExcludes) {
                // Our excludes changed. Update our outgoing edges with the new excludes.
                for (EdgeState outgoingEdge : outgoingEdges) {
                    outgoingEdge.updateTransitiveExcludesAndRequeueTargetNodes(resolutionFilter);
                }
                if (virtualEdges != null) {
                    for (EdgeState virtualEdge : virtualEdges) {
                        virtualEdge.updateTransitiveExcludesAndRequeueTargetNodes(resolutionFilter);
                    }
                }
            }

            if (!ancestorsStrictVersions.equals(previousAncestorsStrictVersions)) {
                // Our strict versions changed. Update our outgoing edges with the new strict versions.
                for (EdgeState outgoingEdge : outgoingEdges) {
                    outgoingEdge.recomputeSelectorAndRequeueTargetNodes(ancestorsStrictVersions, discoveredEdges);
                }
                if (virtualEdges != null) {
                    for (EdgeState virtualEdge : virtualEdges) {
                        virtualEdge.recomputeSelectorAndRequeueTargetNodes(ancestorsStrictVersions, discoveredEdges);
                    }
                }
            }

            visitNewAndInvalidatedDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
            return;
        }

        // We are either doing a fresh visit, or we have some prior state from another visit.
        assert !visitedDependencies || previousTraversalExclusions != null;

        // If we have any prior state, clear it before doing a full visit.
        if (previousTraversalExclusions != null) {
            removeOutgoingEdges();
            edgesToRecompute = null;
        }

        visitDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
        visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
    }

    /**
     * Perform a partial visit of the dependencies of this node, only visiting new constraints
     * and edges that need to be recomputed.
     */
    private void visitNewAndInvalidatedDependencies(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        // Visit any constraints that were previously pending, but are no longer pending.
        if (upcomingNoLongerPendingConstraints != null && potentiallyActivatedConstraints != null) {
            for (ModuleIdentifier module : upcomingNoLongerPendingConstraints) {
                Collection<DependencyState> dependencyStates = potentiallyActivatedConstraints.get(module);
                if (!dependencyStates.isEmpty()) {
                    for (DependencyState dependencyState : dependencyStates) {
                        createAndLinkEdgeState(dependencyState, discoveredEdges, resolutionFilter, ancestorsStrictVersions, false);
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
    void cleanupConstraints() {
        // This part covers constraint that were taken into account between a selection being deferred and this node being scheduled for traversal
        if (upcomingNoLongerPendingConstraints != null) {
            for (ModuleIdentifier identifier : upcomingNoLongerPendingConstraints) {
                ModuleResolveState module = resolveState.getModule(identifier);
                for (EdgeState unattachedEdge : module.getUnattachedEdges()) {
                    if (!unattachedEdge.getSelector().isResolved()) {
                        // Unresolved - we have a selector that was deferred but the constraint has been removed in between
                        NodeState from = unattachedEdge.getFrom();
                        from.prepareToRecomputeEdge(unattachedEdge);
                    }
                }
            }
            upcomingNoLongerPendingConstraints = null;
        }
        // This part covers constraint that might be triggered in the future if the node they point gains a real edge
        if (cachedFilteredDependencyStates != null && !cachedFilteredDependencyStates.isEmpty()) {
            // We may have registered this node as pending if it had constraints.
            // Let's clear that state since it is no longer part of selection
            for (DependencyState dependencyState : cachedFilteredDependencyStates) {
                if (dependencyState.getDependency().isConstraint()) {
                    ModuleResolveState targetModule = resolveState.getModule(dependencyState.getModuleIdentifier(resolveState.getComponentSelectorConverter()));
                    if (targetModule.isPending()) {
                        targetModule.unregisterConstraintProvider(this);
                    }
                }
            }
        }
    }

    private void prepareToRecomputeEdge(EdgeState edgeToRecompute) {
        if (edgesToRecompute == null) {
            edgesToRecompute = new LinkedHashSet<>();
        }
        edgesToRecompute.add(edgeToRecompute);
        resolveState.onMoreSelected(this);
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private void visitDependencies(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        this.potentiallyActivatedConstraints = null;
        this.upcomingNoLongerPendingConstraints = null;

        PendingDependenciesVisitor pendingDepsVisitor = resolveState.newPendingDependenciesVisitor();
        Set<ModuleIdentifier> strictVersionsSet = null;
        for (DependencyState dependencyState : dependencies(resolutionFilter)) {
            PendingDependenciesVisitor.PendingState pendingState = pendingDepsVisitor.maybeAddAsPendingDependency(this, dependencyState);
            if (dependencyState.getDependency().isConstraint()) {
                registerActivatingConstraint(dependencyState);
            }
            if (!pendingState.isPending()) {
                createAndLinkEdgeState(dependencyState, discoveredEdges, resolutionFilter, ancestorsStrictVersions, pendingState == PendingDependenciesVisitor.PendingState.NOT_PENDING_ACTIVATING);
            }
            strictVersionsSet = maybeCollectStrictVersions(strictVersionsSet, dependencyState);
        }
        // If there are 'pending' dependencies that share a target with any of these outgoing edges,
        // then reset the state of the node that owns those dependencies.
        // This way, all edges of the node will be re-processed.
        pendingDepsVisitor.complete();
        storeOwnStrictVersions(strictVersionsSet);

        this.visitedDependencies = true;
    }

    private void registerActivatingConstraint(DependencyState dependencyState) {
        if (potentiallyActivatedConstraints == null) {
            potentiallyActivatedConstraints = LinkedHashMultimap.create();
        }
        potentiallyActivatedConstraints.put(dependencyState.getModuleIdentifier(resolveState.getComponentSelectorConverter()), dependencyState);
    }

    private List<DependencyState> dependencyStates() {
        if (dependenciesMayChange || cachedDependencyStates == null) {
            List<? extends DependencyMetadata> dependencies = getAllDependencies();
            if (transitiveEdgeCount == 0 && metadata.isExternalVariant()) {
                // there must be a single dependency state because this variant is an "available-at"
                // variant and here we are in the case the "including" component said that transitive
                // should be false so we need to arbitrarily carry that onto the dependency metadata
                assert dependencies.size() == 1;
                dependencies = Collections.singletonList(makeNonTransitive(dependencies.get(0)));
            }
            this.cachedDependencyStates = cacheDependencyStates(dependencies);
        }
        return cachedDependencyStates;
    }

    protected List<? extends DependencyMetadata> getAllDependencies() {
        return variantState.getDependencies();
    }

    private static DependencyMetadata makeNonTransitive(DependencyMetadata dependencyMetadata) {
        return new NonTransitiveVariantDependencyMetadata(dependencyMetadata);
    }

    private List<DependencyState> dependencies(ExcludeSpec spec) {
        if (dependenciesMayChange || cachedFilteredDependencyStates == null) {
            this.cachedFilteredDependencyStates = applyDependencyExcludes(spec, dependencyStates());
        }
        return cachedFilteredDependencyStates;
    }

    /**
     * Apply the given excludes to the list of dependency states, filtering out any dependencies
     * that are excluded.
     */
    private List<DependencyState> applyDependencyExcludes(ExcludeSpec spec, List<DependencyState> from) {
        if (from.isEmpty()) {
            return from;
        }
        List<DependencyState> tmp = new ArrayList<>(from.size());
        for (DependencyState dependencyState : from) {
            if (!isExcluded(spec, dependencyState)) {
                tmp.add(dependencyState);
            }
        }
        return tmp;
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private List<DependencyState> cacheDependencyStates(List<? extends DependencyMetadata> dependencies) {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }

        DependencySubstitutionApplicator dependencySubstitutionApplicator = resolveState.getDependencySubstitutionApplicator();
        List<DependencyState> result = new ArrayList<>(dependencies.size());
        for (DependencyMetadata dependency : dependencies) {
            result.add(dependencyStateCache.computeIfAbsent(dependency, dependencySubstitutionApplicator::applySubstitutions));
        }
        return result;
    }

    /**
     * Creates an edge and add it to this node as an outgoing edge.
     */
    private void createAndLinkEdgeState(
        DependencyState dependencyState,
        Collection<EdgeState> discoveredEdges,
        ExcludeSpec resolutionFilter,
        StrictVersionConstraints ancestorsStrictVersions,
        boolean deferSelection
    ) {
        EdgeState dependencyEdge = edgesCache.computeIfAbsent(dependencyState, ds -> new EdgeState(this, ds, resolveState));
        dependencyEdge.updateTransitiveExcludes(resolutionFilter);
        dependencyEdge.computeSelector(ancestorsStrictVersions, discoveredEdges, deferSelection);
        outgoingEdges.add(dependencyEdge);
        dependencyEdge.markUsed();
    }

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     *
     * @param resolutionFilter The excludes inherited from all incoming edges
     * @param ancestorsStrictVersions The strict versions inherited from all incoming edges
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        List<? extends VirtualComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            PendingDependenciesVisitor visitor = resolveState.newPendingDependenciesVisitor();
            for (VirtualComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier platformId = (ModuleComponentIdentifier) owner;

                    // There are 2 possibilities here:
                    // 1. the "platform" referenced is a real module, in which case we directly add it to the graph
                    // 2. the "platform" is a virtual, constructed thing, in which case we add virtual edges to the graph
                    resolvePlatform(platformId);
                    visitVirtualPlatformEdge(discoveredEdges, platformId, ancestorsStrictVersions, resolutionFilter);
                    visitor.markNotPending(platformId.getModuleIdentifier());
                }
            }
            visitor.complete();
        }
    }

    /**
     * Resolve the given platform, creating a lenient platform if the platform does not exist.
     */
    private void resolvePlatform(ModuleComponentIdentifier componentId) {
        ModuleVersionIdentifier toModuleVersionId = DefaultModuleVersionIdentifier.newId(componentId.getModuleIdentifier(), componentId.getVersion());
        ComponentState componentState = resolveState.getModule(componentId.getModuleIdentifier()).getVersion(toModuleVersionId, componentId);
        // We need to check if the target version exists. For this, we have to try to get metadata for the aligned version.
        // If it's there, it means we can align, otherwise, we must NOT add the edge, or resolution would fail
        ComponentGraphResolveState resolvedComponent = componentState.getResolveStateOrNull();

        VirtualPlatformState virtualPlatformState = null;
        if (resolvedComponent == null || resolvedComponent instanceof LenientPlatformGraphResolveState) {
            virtualPlatformState = componentState.getModule().getPlatformState();
            virtualPlatformState.participatingModule(component.getModule());
        }
        if (resolvedComponent == null) {
            // the platform doesn't exist, so we're building a lenient one
            ComponentGraphResolveState newLenientPlatform = LenientPlatformGraphResolveState.of(resolveState.getIdGenerator(), componentId, toModuleVersionId, virtualPlatformState, this, resolveState);
            componentState.setState(newLenientPlatform, ComponentGraphSpecificResolveState.EMPTY_STATE);
            // And now let's make sure we do not have another version of that virtual platform missing its metadata
            componentState.getModule().maybeCreateVirtualMetadata(resolveState);
        }
    }

    /**
     * Creates a virtual edge to the given platform component.
     * The platform may be a real component or a lenient platform component.
     */
    private void visitVirtualPlatformEdge(
        Collection<EdgeState> discoveredEdges,
        ModuleComponentIdentifier componentId,
        StrictVersionConstraints ancestorsStrictVersions,
        ExcludeSpec resolutionFilter
    ) {
        boolean forced = hasStrongOpinion();
        final ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(componentId.getModuleIdentifier(), componentId.getVersion());
        DependencyMetadata dependencyMetadata = new LenientPlatformDependencyMetadata(resolveState, this, selector, componentId, componentId, forced, true);
        DependencyState dependencyState = resolveState.getDependencySubstitutionApplicator().applySubstitutions(dependencyMetadata);
        EdgeState edge = new EdgeState(this, dependencyState, resolveState);
        edge.updateTransitiveExcludes(resolutionFilter);
        edge.computeSelector(ancestorsStrictVersions, discoveredEdges, false);
        if (virtualEdges == null) {
            virtualEdges = new ArrayList<>();
        }
        virtualEdges.add(edge);
        edge.markUsed();
    }

    private boolean hasStrongOpinion() {
        for (EdgeState edgeState : incomingEdges) {
            if (edgeState.getSelector().hasStrongOpinion()) {
                return true;
            }
        }
        return false;
    }

    private boolean isExcluded(ExcludeSpec excludeSpec, DependencyState dependencyState) {
        DependencyMetadata dependency = dependencyState.getDependency();
        if (!resolveState.getEdgeFilter().isSatisfiedBy(dependency)) {
            LOGGER.debug("{} is filtered.", dependency);
            return true;
        }
        if (excludeSpec == moduleExclusions.nothing()) {
            return false;
        }
        ModuleIdentifier targetModuleId = dependencyState.getModuleIdentifier(resolveState.getComponentSelectorConverter());
        if (excludeSpec.excludes(targetModuleId)) {
            LOGGER.debug("{} is excluded from {} by {}.", targetModuleId, this, excludeSpec);
            return true;
        }

        return false;
    }

    void addIncomingEdge(EdgeState dependencyEdge) {
        if (!incomingEdges.contains(dependencyEdge)) {
            incomingEdges.add(dependencyEdge);
            incomingHash += dependencyEdge.hashCode();
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount++;
            }
            requeueChildrenOfEndorsingParent(dependencyEdge);
            cachedModuleResolutionFilter = null;
            resolveState.onMoreSelected(this);
        }
    }

    void removeIncomingEdge(EdgeState dependencyEdge) {
        if (incomingEdges.remove(dependencyEdge)) {
            incomingHash -= dependencyEdge.hashCode();
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount--;
            }
            requeueChildrenOfEndorsingParent(dependencyEdge);
            cachedModuleResolutionFilter = null;
            resolveState.onFewerSelected(this);
        }
    }

    /**
     * Whenever an incoming edge is added or removed from this node, if that edge is
     * endorsing strict versions and this node has strict versions declared, other children
     * of the source node need to be re-processed in order to ensure they handle the updated
     * endorsed strict versions from their parent.
     */
    private void requeueChildrenOfEndorsingParent(EdgeState incomingEdge) {
        if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
            NodeState sourceNode = incomingEdge.getFrom();
            sourceNode.cachedEndorsedStrictVersions = null;
            for (EdgeState edge : sourceNode.getOutgoingEdges()) {
                for (NodeState node : edge.getTargetNodes()) {
                    if (node != this) {
                        resolveState.onMoreSelected(node);
                    }
                }
            }
        }
    }

    void clearTransitiveExclusionsAndEnqueue() {
        cachedModuleResolutionFilter = null;
        // TODO: We can eagerly compute the exclusions and enqueue only on change
        resolveState.onMoreSelected(this);
    }

    @Override
    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    public void evict() {
        evicted = true;
    }

    boolean shouldIncludedInGraphResult() {
        return isSelected() && !component.getModule().isVirtualPlatform();
    }

    private ExcludeSpec computeModuleResolutionFilter(List<EdgeState> incomingEdges) {
        if (metadata.isExternalVariant()) {
            // If the current node represents an external variant, we must not consider its excludes
            // because it's some form of "delegation"
            return moduleExclusions.excludeAny(
                incomingEdges.stream()
                    .map(EdgeState::getTransitiveExclusions)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet())
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
        Set<ExcludeSpec> excludedByBoth = null;
        Set<ExcludeSpec> excludedByEither = null;
        for (EdgeState dependencyEdge : incomingEdges) {
            if (dependencyEdge.isTransitive()) {
                if (edgeExclusions != nothing) {
                    // Transitive dependency
                    ExcludeSpec exclusions = dependencyEdge.getExclusions();
                    if (edgeExclusions == null || exclusions == nothing) {
                        edgeExclusions = exclusions;
                    } else if (edgeExclusions != exclusions) {
                        if (excludedByBoth == null) {
                            excludedByBoth = Sets.newHashSetWithExpectedSize(incomingEdgeCount);
                        }
                        excludedByBoth.add(exclusions);
                    }
                    if (edgeExclusions == nothing) {
                        // if exclusions == nothing, then the intersection will be "nothing"
                        excludedByBoth = null;
                    }
                }
            } else if (dependencyEdge.isConstraint()) {
                excludedByEither = collectEdgeConstraint(nodeExclusions, excludedByEither, dependencyEdge, nothing, incomingEdgeCount);
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

    @Nullable
    private static Set<ExcludeSpec> collectEdgeConstraint(ExcludeSpec nodeExclusions, @Nullable Set<ExcludeSpec> excludedByEither, EdgeState dependencyEdge, ExcludeSpec nothing, int incomingEdgeCount) {
        // Constraint: only consider explicit exclusions declared for this constraint
        ExcludeSpec constraintExclusions = dependencyEdge.getEdgeExclusions();
        if (constraintExclusions != nothing && constraintExclusions != nodeExclusions) {
            if (excludedByEither == null) {
                excludedByEither = Sets.newHashSetWithExpectedSize(incomingEdgeCount);
            }
            excludedByEither.add(constraintExclusions);
        }
        return excludedByEither;
    }

    @Nullable
    private ExcludeSpec joinNodeExclusions(@Nullable ExcludeSpec nodeExclusions, @Nullable Set<ExcludeSpec> excludedByEither) {
        if (excludedByEither != null) {
            if (nodeExclusions != null) {
                excludedByEither.add(nodeExclusions);
                nodeExclusions = moduleExclusions.excludeAny(excludedByEither);
            }
        }
        return nodeExclusions;
    }

    @Nullable
    private ExcludeSpec intersectEdgeExclusions(@Nullable ExcludeSpec edgeExclusions, @Nullable Set<ExcludeSpec> excludedByBoth) {
        if (edgeExclusions == moduleExclusions.nothing()) {
            return edgeExclusions;
        }
        if (excludedByBoth != null) {
            if (edgeExclusions != null) {
                excludedByBoth.add(edgeExclusions);
            }
            edgeExclusions = moduleExclusions.excludeAll(excludedByBoth);
        }
        return edgeExclusions;
    }

    private void collectOwnStrictVersions(ExcludeSpec moduleResolutionFilter) {
        List<DependencyState> dependencies = dependencies(moduleResolutionFilter);
        Set<ModuleIdentifier> constraintsSet = null;
        for (DependencyState dependencyState : dependencies) {
            constraintsSet = maybeCollectStrictVersions(constraintsSet, dependencyState);
        }
        storeOwnStrictVersions(constraintsSet);
    }

    @Nullable
    private Set<ModuleIdentifier> maybeCollectStrictVersions(@Nullable Set<ModuleIdentifier> constraintsSet, DependencyState dependencyState) {
        if (dependencyState.getDependency().getSelector() instanceof ModuleComponentSelector) {
            ModuleComponentSelector selector = (ModuleComponentSelector) dependencyState.getDependency().getSelector();
            if (!StringUtils.isEmpty(selector.getVersionConstraint().getStrictVersion())) {
                if (constraintsSet == null) {
                    constraintsSet = new HashSet<>();
                }
                constraintsSet.add(selector.getModuleIdentifier());
            }
        }
        return constraintsSet;
    }

    private void storeOwnStrictVersions(@Nullable Set<ModuleIdentifier> constraintsSet) {
        StrictVersionConstraints newStrictVersions = constraintsSet == null
            ? StrictVersionConstraints.EMPTY
            : StrictVersionConstraints.of(ImmutableSet.copyOf(constraintsSet));

        if (ownStrictVersions != null && !ownStrictVersions.equals(newStrictVersions)) {
            // Our strict versions were already computed, and they just changed.
            // Invalidate any nodes that computed their endorsed strict versions based on our previous value.
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
                    incomingEdge.getFrom().cachedEndorsedStrictVersions = null;
                }
            }
        }

        this.ownStrictVersions = newStrictVersions;
    }

    /**
     * Determines all strict versions inherited from ancestors. When a node declares strict
     * versions, either through its own dependencies, or by endorsement, those strict versions apply
     * to all descendants of that node's exclusive subgraph. If a given node belongs to multiple
     * subgraphs, a strict version is only inherited if all parent subgraphs provide a
     * strict version for that module. For this reason, we compute the intersection of strict
     * versions coming from all incoming edges.
     */
    private StrictVersionConstraints collectAncestorsStrictVersions() {
        if (incomingEdges.isEmpty()) {
            return StrictVersionConstraints.EMPTY;
        }

        if (incomingEdges.size() == 1) {
            return getStrictVersionsForEdge(incomingEdges.get(0));
        }

        StrictVersionConstraints ancestorsStrictVersions = null;
        for (EdgeState dependencyEdge : incomingEdges) {
            StrictVersionConstraints allEdgeStrictVersions = getStrictVersionsForEdge(dependencyEdge);

            ancestorsStrictVersions = ancestorsStrictVersions == null
                ? allEdgeStrictVersions
                : ancestorsStrictVersions.intersect(allEdgeStrictVersions);

            if (ancestorsStrictVersions == StrictVersionConstraints.EMPTY) {
                // No need to continue. Empty intersected with anything is empty.
                break;
            }
        }
        return ancestorsStrictVersions;
    }

    /**
     * Determine the strict versions inherited through a given edge.
     */
    private StrictVersionConstraints getStrictVersionsForEdge(EdgeState dependencyEdge) {
        NodeState from = dependencyEdge.getFrom();
        StrictVersionConstraints parentStrongStrictVersions = from.getStrongStrictVersions();
        StrictVersionConstraints parentEndorsedStrictVersions = from.getEndorsedStrictVersions();

        // If the source node endorses us, then we might be the source of a strict version that it
        // endorses. For this reason, we inherit a parent's endorsed strict versions only if we may
        // not be the source of that strict version.
        StrictVersionConstraints filteredEndorsedStrictVersions;
        if (dependencyEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
            filteredEndorsedStrictVersions = parentEndorsedStrictVersions.minus(ownStrictVersions);
        } else {
            filteredEndorsedStrictVersions = parentEndorsedStrictVersions;
        }

        return parentStrongStrictVersions.union(filteredEndorsedStrictVersions);
    }

    /**
     * Get the strong strict versions of this node -- the strict versions that are sourced from higher up
     * in the graph. These strong strict versions take precedence over endorsed strict versions.
     */
    private StrictVersionConstraints getStrongStrictVersions() {
        // This method assumes that ownStrictVersions and previousAncestorsStrictVersions
        // have already been computed for the source node. If these values ever change, we must
        // ensure this node is re-processed.
        assert ownStrictVersions != null;
        assert previousAncestorsStrictVersions != null;
        return ownStrictVersions.union(previousAncestorsStrictVersions);
    }

    private StrictVersionConstraints getEndorsedStrictVersions() {
        if (cachedEndorsedStrictVersions == null) {
            this.cachedEndorsedStrictVersions = computeEndorsedStrictVersions();
        }
        return this.cachedEndorsedStrictVersions;
    }

    /**
     * Determine all strict versions endorsed by this node.
     */
    private StrictVersionConstraints computeEndorsedStrictVersions() {
        StrictVersionConstraints endorsedStrictVersions = StrictVersionConstraints.EMPTY;
        for (EdgeState edgeState : outgoingEdges) {
            if (edgeState.getDependencyState().getDependency().isEndorsingStrictVersions()) {
                for (NodeState endorsedNode : edgeState.getTargetNodes()) {
                    if (endorsedNode.ownStrictVersions == null) {
                        // The node's dependencies were not yet visited. Compute them now.
                        endorsedNode.collectOwnStrictVersions(endorsedNode.computeModuleResolutionFilter(endorsedNode.incomingEdges));
                    }
                    endorsedStrictVersions = endorsedStrictVersions.union(endorsedNode.ownStrictVersions);
                }
            }
        }
        return endorsedStrictVersions;
    }

    private boolean sameIncomingEdgesAsPreviousPass(int incomingEdgeCount) {
        // This is a heuristic, more than truth: it is possible that the 2 long hashs
        // are identical AND that the sizes of collections are identical, but it's
        // extremely unlikely (never happened on test cases even on large dependency graph)
        return cachedModuleResolutionFilter != null
            && previousIncomingHash == incomingHash
            && previousIncomingEdgeCount == incomingEdgeCount;
    }

    private void removeOutgoingEdges() {
        boolean alreadyRemoving = removingOutgoingEdges;
        removingOutgoingEdges = true;
        if (!outgoingEdges.isEmpty() && !alreadyRemoving) {
            for (EdgeState outgoingEdge : outgoingEdges) {
                outgoingEdge.markUnused(); // Track that these edges have been removed from outgoing but maybe not from incoming, in case we hit one of these continues and do not call disconnectOutgoingEdge
                ComponentState targetComponent = outgoingEdge.getTargetComponent();
                if (targetComponent == component) {
                    // if the same component depends on itself: do not attempt to cleanup the same thing several times
                    continue;
                }
                if (targetComponent != null && targetComponent.getModule().isChangingSelection()) {
                    // don't requeue something which is already changing selection
                    continue;
                }

                disconnectOutgoingEdge(outgoingEdge);
            }
            outgoingEdges.clear();
        }
        if (virtualEdges != null /*&& !removingOutgoing*/) {
            for (EdgeState virtualEdge : virtualEdges) {
                virtualEdge.markUnused();
                disconnectOutgoingEdge(virtualEdge);
            }
            virtualEdges = null;
        }
        previousTraversalExclusions = null;
        previousAncestorsStrictVersions = null;
        visitedDependencies = false;
        cachedFilteredDependencyStates = null;
        virtualPlatformNeedsRefresh = false;
        removingOutgoingEdges = alreadyRemoving;
    }

    private void disconnectOutgoingEdge(EdgeState outgoingEdge) {
        outgoingEdge.detachFromTargetNodes();
        outgoingEdge.getSelector().getTargetModule().disconnectIncomingEdge(this, outgoingEdge);
        outgoingEdge.clearSelector();
    }

    public void restart(ComponentState selected) {
        // Restarting this configuration after conflict resolution.
        // If this configuration belongs to the select version, queue ourselves up for traversal.
        // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
        if (component == selected) {
            if (!evicted) {
                resolveState.onMoreSelected(this);
                return;
            }
        }
        if (!incomingEdges.isEmpty()) {
            restartIncomingEdges();
        }
    }

    private void restartIncomingEdges() {
        if (incomingEdges.size() == 1) {
            EdgeState singleEdge = incomingEdges.get(0);
            singleEdge.retarget();
        } else {
            for (EdgeState edge : new ArrayList<>(incomingEdges)) {
                edge.retarget();
            }
        }
        // TODO: Restarting incoming edges should ensure they are pointing to the correct node.
        // If they end up pointing to us after restart, we should not remove them.
        clearIncomingEdges();
    }

    private void clearIncomingEdges() {
        incomingEdges.clear();
        incomingHash = 0;
        transitiveEdgeCount = 0;
    }

    public void deselect() {
        removeOutgoingEdges();
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
        resolveState.onFewerSelected(this);
    }

    /**
     * Invoked when this node is back to being a pending dependency.
     * There may be some incoming edges left at that point, but they must all be coming from constraints.
     */
    public void clearIncomingConstraints(PendingDependencies pendingDependencies, NodeState backToPendingSource) {
        if (incomingEdges.isEmpty()) {
            return;
        }
        // Cleaning has to be done on a copied collection because of the recompute happening on selector removal
        List<EdgeState> remainingIncomingEdges = ImmutableList.copyOf(incomingEdges);
        clearIncomingEdges();
        for (EdgeState incomingEdge : remainingIncomingEdges) {
            assert incomingEdge.isConstraint();
            NodeState from = incomingEdge.getFrom();
            if (from != backToPendingSource) {
                // Only remove edges that come from a different node than the source of the dependency going back to pending
                // The edges from the "From" will be removed first
                from.removeOutgoingEdge(incomingEdge);
            }
            pendingDependencies.registerConstraintProvider(from);
        }
    }

    void removeOutgoingEdge(EdgeState edge) {
        if (!removingOutgoingEdges) {
            // don't try to remove an outgoing edge if we're already doing it
            // because removeOutgoingEdges() will clear all of them so it's not required to do it twice
            // and it can cause a concurrent modification exception
            outgoingEdges.remove(edge);
            edge.markUnused();
            edge.clearSelector();
        }
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

    void makePending(EdgeState edgeState) {
        if (!removingOutgoingEdges) {
            // We can ignore if we are already removing edges anyway
            outgoingEdges.remove(edgeState);
            edgeState.markUnused();
            edgeState.clearSelector();
        }
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
                return outgoingEdge.getSelectedNode();
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
            if (outgoingEdge.getTargetComponent() != null) {
                for (NodeState nodeState : outgoingEdge.getTargetComponent().getNodes()) {
                    if (visited.add(nodeState)) {
                        nodeState.dependsTransitivelyOn(visited);
                    }
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
        public DependencyMetadata withTargetAndArtifacts(ComponentSelector target, List<IvyArtifactName> artifacts) {
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
