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
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.SubstitutionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints;
import org.gradle.api.internal.capabilities.ImmutableCapability;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.Try;
import org.gradle.internal.collect.PersistentSet;
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

    private boolean queued;
    private @Nullable NodeState replacement;
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

    // Caches the list of edges which are NOT excluded
    private @Nullable List<EdgeState> cachedFilteredEdges;

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
     * The transitive strict versions from inherited from parents.
     */
    @VisibleForTesting
    StrictVersionConstraints ancestorsStrictVersions = StrictVersionConstraints.EMPTY;

    /**
     * Our own strict version constraints, from the previous graph traversal.
     */
    @VisibleForTesting
    @Nullable StrictVersionConstraints ownStrictVersions;

    /**
     * Cached copy of all endorsed strict versions. Must be invalidated whenever
     * an outgoing endorsing edge is added or removed, or if the target endorsed
     * node's own strict versions change.
     */
    private @Nullable StrictVersionConstraints cachedEndorsedStrictVersions;

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

    @Override
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
        StrictVersionConstraints ancestorsStrictVersions = this.ancestorsStrictVersions;

        doVisitDependencies(resolutionFilter, ancestorsStrictVersions, discoveredEdges);

        assert (previousTraversalExclusions == null) == (previousAncestorsStrictVersions == null);
        this.previousTraversalExclusions = resolutionFilter;
        this.previousAncestorsStrictVersions = ancestorsStrictVersions;
    }

    private void doVisitDependencies(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        if (transitiveEdgeCount == 0 && !isRoot() && canIgnoreExternalVariant()) {
            assert !incomingEdges.isEmpty();

            // This node is part of the graph, but no incoming edges are transitive.
            // Act as if we have no declared dependencies. Remove any outgoing edges we may
            // have from a previous traversal. Virtual platform edges remain in order to
            // maintain version alignment (this behavior differs from non-virtual platform
            // edges, which is confusing and potentially not desired).
            removeOutgoingEdges();
            if (this.ownStrictVersions == null) {
                // Compute our own strict versions here, as we are short-circuiting
                // `visitDependencies`, which usually collects them.
                collectOwnStrictVersions(resolutionFilter);
            }
            visitOwners(resolutionFilter, ancestorsStrictVersions, discoveredEdges);
            return;
        }

        // If we have visited our dependencies before, we can in some cases skip a complete visit.
        boolean sameExcludes = resolutionFilter.equals(previousTraversalExclusions);
        if (visitedDependencies
            && !virtualPlatformNeedsRefresh
            && (sameExcludes || computeFilteredEdges(resolutionFilter).equals(this.cachedFilteredEdges))
        ) {
            // Our excludes did not change, or after applying new excludes to our outgoing dependencies,
            // the filtered dependencies did not change. We have the same dependencies as the previous traversal.

            if (!sameExcludes) {
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
        assert !visitedDependencies || previousTraversalExclusions != null;

        // If we have any prior state, clear it before doing a full visit.
        removeOutgoingEdges();

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
        if (cachedFilteredEdges != null && !cachedFilteredEdges.isEmpty()) {
            // We may have registered this node as pending if it had constraints.
            // Let's clear that state since it is no longer part of selection
            for (EdgeState edge : cachedFilteredEdges) {
                if (edge.getDependencyMetadata().isConstraint()) {
                    ModuleResolveState targetModule = resolveState.getModule(edge.getDependencyState().getModuleIdentifier(resolveState.getComponentSelectorConverter()));
                    if (targetModule.isPending()) {
                        targetModule.unregisterConstraintProvider(this);
                    }
                }
            }
        }
    }

    void prepareToRecomputeEdge(EdgeState edgeToRecompute) {
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

        PersistentSet<ModuleIdentifier> strictVersionsSet = PersistentSet.of();
        for (EdgeState edge : edges(resolutionFilter)) {
            registerOutgoingEdge(resolutionFilter, ancestorsStrictVersions, discoveredEdges, edge);
            strictVersionsSet = maybeCollectStrictVersions(strictVersionsSet, edge.getDependencyMetadata().getSelector());
        }

        // If there are 'pending' dependencies that share a target with any of these outgoing edges,
        // then reset the state of the node that owns those dependencies.
        // This way, all edges of the node will be re-processed.
        storeOwnStrictVersions(strictVersionsSet);
        this.visitedDependencies = true;
    }

    private void registerOutgoingEdge(
        ExcludeSpec resolutionFilter,
        StrictVersionConstraints ancestorsStrictVersions,
        Collection<EdgeState> discoveredEdges,
        EdgeState dependencyEdge
    ) {
        boolean constraint = dependencyEdge.getDependencyMetadata().isConstraint();
        ModuleIdentifier moduleId = dependencyEdge.getDependencyState().getModuleIdentifier(resolveState.getComponentSelectorConverter());
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

    private List<EdgeState> edges(ExcludeSpec spec) {
        if (dependenciesMayChange || cachedFilteredEdges == null) {
            this.cachedFilteredEdges = computeFilteredEdges(spec);
        }
        return cachedFilteredEdges;
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
            // TODO: Ideally DependencyMetadata would already provide an ImmutableList of artifacts
            ImmutableList.copyOf(dependency.getArtifacts())
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
     *
     * @param resolutionFilter The excludes inherited from all incoming edges
     * @param ancestorsStrictVersions The strict versions inherited from all incoming edges
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(ExcludeSpec resolutionFilter, StrictVersionConstraints ancestorsStrictVersions, Collection<EdgeState> discoveredEdges) {
        List<? extends VirtualComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            for (VirtualComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier platformId = (ModuleComponentIdentifier) owner;

                    // There are 2 possibilities here:
                    // 1. the "platform" referenced is a real module, in which case we directly add it to the graph
                    // 2. the "platform" is a virtual, constructed thing, in which case we add virtual edges to the graph
                    resolvePlatform(platformId);

                    boolean forced = hasStrongOpinion();
                    final ModuleComponentSelector selector = DefaultModuleComponentSelector.newSelector(platformId.getModuleIdentifier(), platformId.getVersion());
                    DependencyMetadata dependencyMetadata = new LenientPlatformDependencyMetadata(resolveState, this, selector, platformId, platformId, forced, true, false);
                    EdgeState virtualPlatformEdge = createEdge(dependencyMetadata);

                    registerOutgoingEdge(
                        resolutionFilter,
                        ancestorsStrictVersions,
                        discoveredEdges,
                        virtualPlatformEdge
                    );
                }
            }
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
        ModuleIdentifier targetModuleId = edgeState.getDependencyState().getModuleIdentifier(resolveState.getComponentSelectorConverter());
        if (excludeSpec.excludes(targetModuleId)) {
            LOGGER.debug("{} is excluded from {} by {}.", targetModuleId, this, excludeSpec);
            return true;
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
            requeueChildrenOfEndorsingParent(dependencyEdge);

            if (incomingEdges.size() == 1) {
                updateAncestorsStrictVersions(getStrictVersionsForEdge(dependencyEdge));
            } else {
                updateAncestorsStrictVersions(ancestorsStrictVersions.intersect(getStrictVersionsForEdge(dependencyEdge)));
            }

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
            requeueChildrenOfEndorsingParent(dependencyEdge);
            recomputeAncestorsStrictVersions();
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
            requeueChildrenOfEndorsingParent(incomingEdge);
        }
        updateAncestorsStrictVersions(StrictVersionConstraints.EMPTY);
        resolveState.onFewerSelected(this);

        return removedEdges;
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
            sourceNode.invalidateEndorsedStrictVersions();
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

    /**
     * Mark this node as being evicted by another node in the same component,
     * after these two nodes entered a capability conflict and the conflict
     * was resolved with the given node as the winner and this node as a loser.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public void replaceWith(@Nullable NodeState replacement) {
        assert replacement == null || replacement.getComponent() == getComponent();
        this.replacement = replacement;
    }

    /**
     * The node in the same component as this node, that won against this node
     * during capability conflict resolution, if any.
     */
    public @Nullable NodeState getReplacement() {
        return replacement;
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

    @VisibleForTesting
    void collectOwnStrictVersions(ExcludeSpec moduleResolutionFilter) {
        List<EdgeState> edges = edges(moduleResolutionFilter);
        PersistentSet<ModuleIdentifier> constraintsSet = PersistentSet.of();
        for (EdgeState edge : edges) {
            constraintsSet = maybeCollectStrictVersions(constraintsSet, edge.getDependencyMetadata().getSelector());
        }
        storeOwnStrictVersions(constraintsSet);
    }

    private static PersistentSet<ModuleIdentifier> maybeCollectStrictVersions(PersistentSet<ModuleIdentifier> constraintsSet, ComponentSelector selector) {
        if (selector instanceof ModuleComponentSelector) {
            ModuleComponentSelector mcs = (ModuleComponentSelector) selector;
            if (!StringUtils.isEmpty(mcs.getVersionConstraint().getStrictVersion())) {
                constraintsSet = constraintsSet.plus(mcs.getModuleIdentifier());
            }
        }
        return constraintsSet;
    }

    private void storeOwnStrictVersions(PersistentSet<ModuleIdentifier> constraintsSet) {
        StrictVersionConstraints newStrictVersions = StrictVersionConstraints.of(constraintsSet);

        StrictVersionConstraints existingOwnStrictVersions = this.ownStrictVersions;
        this.ownStrictVersions = newStrictVersions;

        if (existingOwnStrictVersions == null) {
            // If our existing strict versions are null, nobody else has observed them,
            // so their value being initialized for the first time will no invalidate
            // any existing calculated strict versions.
            return;
        }

        if (!newStrictVersions.equals(existingOwnStrictVersions)) {
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.getDependencyMetadata().isEndorsingStrictVersions()) {
                    // Our own strict versions contribute to the endorsed strict versions of
                    // ancestors that endorse us.
                    incomingEdge.getFrom().invalidateEndorsedStrictVersions();
                    // Our own strict versions contribute to our ancestors strict versions
                    // if our ancestor endorses us.
                    recomputeAncestorsStrictVersions();
                }
            }
            for (EdgeState outgoingEdge : outgoingEdges) {
                for (NodeState targetNode : outgoingEdge.getTargetNodes()) {
                    // Our own strict versions contribute to our descendants strict versions.
                    targetNode.recomputeAncestorsStrictVersions();
                }
            }
        }
    }

    /**
     * Recompute the strict versions inherited from ancestors,
     * propagating the new value to all descendants.
     */
    @VisibleForTesting
    void recomputeAncestorsStrictVersions() {
        updateAncestorsStrictVersions(collectAncestorsStrictVersions());
    }

    /**
     * Set the strict versions inherited from ancestors,
     * propagating the new value to all descendants.
     */
    private void updateAncestorsStrictVersions(StrictVersionConstraints newAncestorsStrictVersions) {
        if (newAncestorsStrictVersions.equals(this.ancestorsStrictVersions)) {
            // No change, no need to propagate further.
            return;
        }

        this.ancestorsStrictVersions = newAncestorsStrictVersions;

        for (EdgeState outgoingEdge : outgoingEdges) {
            for (NodeState targetNode : outgoingEdge.getTargetNodes()) {
                // The ancestors strict versions of this node contribute to the
                // ancestors strict versions of our children.
                targetNode.recomputeAncestorsStrictVersions();
            }
        }
    }

    /**
     * Determines all strict versions inherited from ancestors. When a node declares strict
     * versions, either through its own dependencies, or by endorsement, those strict versions apply
     * to all descendants of that node's exclusive subgraph. If a given node belongs to multiple
     * subgraphs, a strict version is only inherited if all parent subgraphs provide a
     * strict version for that module. For this reason, we compute the intersection of strict
     * versions coming from all incoming edges.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    private StrictVersionConstraints collectAncestorsStrictVersions() {
        if (incomingEdges.isEmpty()) {
            return StrictVersionConstraints.EMPTY;
        }

        if (incomingEdges.size() == 1) {
            EdgeState dependencyEdge = incomingEdges.get(0);
            if (dependencyEdge.getFrom().isSelected()) {
                return getStrictVersionsForEdge(dependencyEdge);
            } else {
                return StrictVersionConstraints.EMPTY;
            }
        }

        StrictVersionConstraints ancestorsStrictVersions = null;
        for (EdgeState dependencyEdge : incomingEdges) {
            if (!dependencyEdge.getFrom().isSelected()) {
                continue;
            }
            StrictVersionConstraints allEdgeStrictVersions = getStrictVersionsForEdge(dependencyEdge);

            ancestorsStrictVersions = ancestorsStrictVersions == null
                ? allEdgeStrictVersions
                : ancestorsStrictVersions.intersect(allEdgeStrictVersions);

            if (ancestorsStrictVersions == StrictVersionConstraints.EMPTY) {
                // No need to continue. Empty intersected with anything is empty.
                break;
            }
        }
        return ancestorsStrictVersions != null ?  ancestorsStrictVersions : StrictVersionConstraints.EMPTY;
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
        // This method assumes that `ownStrictVersions` has already been
        // computed for the source node. If `ownStrictVersions` ever changes,
        // we must ensure this node is re-processed.
        assert ownStrictVersions != null;
        return ownStrictVersions.union(ancestorsStrictVersions);
    }

    /**
     * Invalidate the cached strict versions endorsed by this node,
     * propagating the invalidation to all descendants.
     */
    private void invalidateEndorsedStrictVersions() {
        this.cachedEndorsedStrictVersions = null;

        for (EdgeState outgoingEdge : outgoingEdges) {
            for (NodeState targetNode : outgoingEdge.getTargetNodes()) {
                // The endorsed strict versions of this node contributes to the
                // ancestors strict versions of our children.
                targetNode.recomputeAncestorsStrictVersions();
            }
        }
    }

    /**
     * Get the strict versions endorsed by this node, calculating the value if necessary.
     */
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
            if (edgeState.getDependencyMetadata().isEndorsingStrictVersions()) {
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

    /**
     * Returns true if {@link #visitOutgoingDependenciesAndCollectEdges(Collection)}
     * has never been called, or if it has been called but {@link #removeOutgoingEdges()}
     * has been called since then.
     * <p>
     * If this returns true, this node has no outgoing edges in the graph, and therefore does
     * not affect the rest of the graph.
     */
    boolean isDisconnected() {
        return previousTraversalExclusions == null && !visitedDependencies;
    }

    /**
     * This method is effectively the inverse of {@link #visitOutgoingDependenciesAndCollectEdges(Collection)}.
     * <p>
     * Cleans up the outgoing state of this node, undoing any effects this node has on the graph.
     * To be called when this node is removed from the graph.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public void removeOutgoingEdges() {
        if (previousTraversalExclusions == null) {
            return;
        }

        if (!outgoingEdges.isEmpty()) {
            for (EdgeState outgoingEdge : outgoingEdges) {
                disconnectOutgoingEdge(outgoingEdge);
            }
            outgoingEdges.clear();
        }
        cleanupConstraints();
        previousTraversalExclusions = null;
        previousAncestorsStrictVersions = null;
        visitedDependencies = false;
        cachedFilteredEdges = null;
        edgesToRecompute = null;
        virtualPlatformNeedsRefresh = false;
    }

    private void disconnectOutgoingEdge(EdgeState outgoingEdge) {
        outgoingEdge.detachFromTargetNodes();
        outgoingEdge.getSelector().getTargetModule().disconnectIncomingEdge(this, outgoingEdge);
    }

    /**
     * Called for each participant of a conflict after the conflict was resolved.
     */
    @SuppressWarnings("ReferenceEquality") //TODO: evaluate errorprone suppression (https://github.com/gradle/gradle/issues/35864)
    public void restart(ComponentState selected) {
        if (component == selected && replacement == null) {
            // We are in the selected component and are not replaced by another node in our own component.
            // We are the winning node. Queue ourselves up for traversal.
            resolveState.onMoreSelected(this);
        } else {
            // We are the losing node. Retarget all incoming edges so they are attached to their correct nodes.
            restartIncomingEdges();
        }
    }

    /**
     * Called on losing nodes after conflict resolution to retarget their existing incoming
     * edges to the winning node. This method must be called after any relevant state is updated
     * so that retargeting chooses the correct new target node.
     */
    private void restartIncomingEdges() {
        if (incomingEdges.size() == 1) {
            EdgeState singleEdge = incomingEdges.get(0);
            singleEdge.retarget();
        } else if (incomingEdges.size() > 1){
            for (EdgeState edge : new ArrayList<>(incomingEdges)) {
                edge.retarget();
            }
        }

        // This method is called on a node that fails conflict resolution. If, after retargeting,
        // we still have incoming edges, something went wrong.
        assert incomingEdges.isEmpty();
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
