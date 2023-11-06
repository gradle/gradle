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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.ArtifactSelectionDetailsInternal;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.ResolvedGraphVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CapabilitiesConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.strict.StrictVersionConstraints;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.capabilities.ShadowedCapability;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.VirtualComponentIdentifier;
import org.gradle.internal.component.local.model.LocalConfigurationGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentGraphResolveState;
import org.gradle.internal.component.model.ComponentGraphSpecificResolveState;
import org.gradle.internal.component.model.DelegatingDependencyMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.VariantGraphResolveMetadata;
import org.gradle.internal.component.model.VariantGraphResolveState;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    private final Long nodeId;
    private final ComponentState component;
    private final List<EdgeState> incomingEdges = Lists.newArrayList();
    private final List<EdgeState> outgoingEdges = Lists.newArrayList();
    private final ResolvedConfigurationIdentifier id;

    private final VariantGraphResolveState variantState;
    private final VariantGraphResolveMetadata metadata;
    private final ResolveState resolveState;
    private final ModuleExclusions moduleExclusions;
    private final boolean isTransitive;
    private final boolean selectedByVariantAwareResolution;
    private final boolean dependenciesMayChange;
    private boolean doesNotHaveDependencies;

    @Nullable
    ExcludeSpec previousTraversalExclusions;

    // In opposite to outgoing edges, virtual edges are for now pretty rare, so they are created lazily
    private List<EdgeState> virtualEdges;
    private boolean queued;
    private boolean evicted;
    private int transitiveEdgeCount;
    private Set<ModuleIdentifier> upcomingNoLongerPendingConstraints;
    private boolean virtualPlatformNeedsRefresh;
    private Set<EdgeState> edgesToRecompute;
    private Multimap<ModuleIdentifier, DependencyState> potentiallyActivatedConstraints;

    // caches
    private final Map<DependencyMetadata, DependencyState> dependencyStateCache = Maps.newHashMap();
    private final Map<DependencyState, EdgeState> edgesCache = Maps.newHashMap();

    // Caches the list of dependency states for dependencies
    private List<DependencyState> cachedDependencyStates;

    // Caches the list of dependency states which are NOT excluded
    private List<DependencyState> cachedFilteredDependencyStates;

    // exclusions optimizations
    private ExcludeSpec cachedNodeExclusions;
    private int previousIncomingEdgeCount;
    private long previousIncomingHash;
    private long incomingHash;
    private ExcludeSpec cachedModuleResolutionFilter;

    private StrictVersionConstraints ancestorsStrictVersionConstraints;
    private StrictVersionConstraints ownStrictVersionConstraints;
    private List<EdgeState> endorsesStrictVersionsFrom;
    private boolean removingOutgoingEdges;
    private boolean findingExternalVariants;

    @VisibleForTesting // just for testing purposes
    public NodeState(long resultId, ResolvedConfigurationIdentifier id, ComponentState component, VariantGraphResolveState variant, boolean selectedByVariantAwareResolution) {
        this(resultId, id, component, null, variant, selectedByVariantAwareResolution);
    }

    public NodeState(long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, VariantGraphResolveState variant, boolean selectedByVariantAwareResolution) {
        this.nodeId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.variantState = variant;
        this.metadata = variant.getMetadata();
        this.isTransitive = metadata.isTransitive() || metadata.isExternalVariant();
        this.selectedByVariantAwareResolution = selectedByVariantAwareResolution;
        this.moduleExclusions = resolveState == null ? null : resolveState.getModuleExclusions(); // can be null in tests, ResolveState cannot be mocked
        this.dependenciesMayChange = component.getModule() != null && component.getModule().isVirtualPlatform(); // can be null in tests, ComponentState cannot be mocked
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

    @Override
    public ComponentState getComponent() {
        return component;
    }

    @Override
    public Long getNodeId() {
        return nodeId;
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
    public VariantGraphResolveMetadata getMetadata() {
        return metadata;
    }

    @Override
    public VariantGraphResolveState getResolveState() {
        return variantState;
    }

    @Override
    public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
        if (metadata instanceof LocalConfigurationGraphResolveMetadata) {
            // Only when this node has a transitive incoming edge
            for (EdgeState incomingEdge : incomingEdges) {
                if (incomingEdge.isTransitive()) {
                    return ((LocalConfigurationGraphResolveMetadata) metadata).getFiles();
                }
            }
        }
        return Collections.emptySet();
    }

    @Override
    public String toString() {
        return String.format("%s(%s)", component, id.getConfiguration());
    }

    public String getSimpleName() {
        return component.getId().toString();
    }

    public String getNameWithVariant() {
        return component.getId() + " variant " + id.getConfiguration();
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
        //      If net exclusions for this node have changed, remove previous state and traverse outgoing edges again.

        if (!component.isSelected()) {
            LOGGER.debug("version for {} is not selected. ignoring.", this);
            cleanupConstraints();
            return;
        }

        // Check if there are any transitive incoming edges at all. Don't traverse if not.
        if (transitiveEdgeCount == 0 && !isRoot() && canIgnoreExternalVariant()) {
            handleNonTransitiveNode(discoveredEdges);
            return;
        }

        // Determine the net exclusion for this node, by inspecting all transitive incoming edges
        ExcludeSpec resolutionFilter = computeModuleResolutionFilter(incomingEdges);

        // Virtual platforms require their constraints to be recomputed each time as each module addition can cause a shift in versions
        if (!isVirtualPlatformNeedsRefresh()) {
            // Check if node was previously traversed with the same net exclusion when not a virtual platform
            if (excludesSameDependenciesAsPreviousTraversal(resolutionFilter)) {
                boolean newConstraints = handleNewConstraints(discoveredEdges);
                boolean edgesToRecompute = handleEdgesToRecompute(discoveredEdges);
                if (!newConstraints && !edgesToRecompute) {
                    // Was previously traversed, and no change to the set of modules that are linked by outgoing edges.
                    // Don't need to traverse again, but hang on to the new filter since it may change the set of excluded artifacts.
                    LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                }
                previousTraversalExclusions = resolutionFilter;
                return;
            }
        }

        // Clear previous traversal state, if any
        if (previousTraversalExclusions != null) {
            removeOutgoingEdges();
            edgesToRecompute = null;
            potentiallyActivatedConstraints = null;
            ownStrictVersionConstraints = null;
        }
        // We are processing dependencies, anything in the previous state will be handled
        upcomingNoLongerPendingConstraints = null;

        visitDependencies(resolutionFilter, discoveredEdges);
        visitOwners(discoveredEdges);
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
                for (EdgeState unattachedDependency : module.getUnattachedDependencies()) {
                    if (!unattachedDependency.getSelector().isResolved()) {
                        // Unresolved - we have a selector that was deferred but the constraint has been removed in between
                        NodeState from = unattachedDependency.getFrom();
                        from.prepareToRecomputeEdge(unattachedDependency);
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
                    ModuleResolveState targetModule = resolveState.getModule(dependencyState.getModuleIdentifier());
                    if (targetModule.isPending()) {
                        targetModule.unregisterConstraintProvider(this);
                    }
                }
            }
        }
    }

    private boolean excludesSameDependenciesAsPreviousTraversal(ExcludeSpec newResolutionFilter) {
        List<DependencyState> oldStates = cachedFilteredDependencyStates;
        if (previousTraversalExclusions == null || oldStates == null) {
            return false;
        }
        if (previousTraversalExclusions.equals(newResolutionFilter)) {
            return true;
        }
        if (doesNotHaveDependencies && !dependenciesMayChange) {
            // whatever the exclude filter, there are no dependencies
            return true;
        }
        cachedFilteredDependencyStates = null;
        // here, we need to check that applying the new resolution filter
        // we would actually exclude exactly the same dependencies as in
        // the previous visit. It is important that this is NOT a heuristic
        // (it used to be) because if the filters are _equivalent_, we would
        // revisit all dependencies and possibly change the classpath order!
        boolean sameDependencies = dependencies(newResolutionFilter).equals(oldStates);
        if (sameDependencies) {
            // While there will be no change to this node, there might be changes to the nodes it brings as the exclude change could concern them
            for (EdgeState outgoingEdge : outgoingEdges) {
                outgoingEdge.updateTransitiveExcludes(newResolutionFilter);
            }
        }
        if (LOGGER.isDebugEnabled()) {
            if (sameDependencies) {
                LOGGER.debug("Filter {} excludes same dependencies as previous {}. Dependencies left = {}", newResolutionFilter, previousTraversalExclusions, oldStates);
            } else {
                LOGGER.debug("Filter {} doesn't exclude same dependencies as previous {}. Previous dependencies left = {} - New dependencies left = {}", newResolutionFilter, previousTraversalExclusions, oldStates, cachedFilteredDependencyStates);
            }
        }
        return sameDependencies;
    }

    private void prepareToRecomputeEdge(EdgeState edgeToRecompute) {
        if (edgesToRecompute == null) {
            edgesToRecompute = Sets.newLinkedHashSet();
        }
        edgesToRecompute.add(edgeToRecompute);
        resolveState.onMoreSelected(this);
    }

    private boolean handleEdgesToRecompute(Collection<EdgeState> discoveredEdges) {
        if (edgesToRecompute != null) {
            discoveredEdges.addAll(edgesToRecompute);
            edgesToRecompute = null;
            return true;
        }
        return false;
    }

    private boolean handleNewConstraints(Collection<EdgeState> discoveredEdges) {
        if (upcomingNoLongerPendingConstraints != null) {
            // Previously traversed but new constraints no longer pending, so partial traversing
            visitAdditionalConstraints(discoveredEdges);
            return true;
        }
        return false;
    }

    private boolean isVirtualPlatformNeedsRefresh() {
        return virtualPlatformNeedsRefresh;
    }

    /**
     * Removes outgoing edges from no longer transitive node
     * Also process {@code belongsTo} if node still has edges at all.
     *
     * @param discoveredEdges In/Out parameter collecting dependencies or platforms
     */
    private void handleNonTransitiveNode(Collection<EdgeState> discoveredEdges) {
        cleanupConstraints();
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

    private DependencyState createDependencyState(DependencyMetadata md) {
        return new DependencyState(md, resolveState.getComponentSelectorConverter());
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private void visitDependencies(ExcludeSpec resolutionFilter, Collection<EdgeState> discoveredEdges) {
        PendingDependenciesVisitor pendingDepsVisitor = resolveState.newPendingDependenciesVisitor();
        Set<ModuleIdentifier> strictVersionsSet = null;
        boolean shouldComputeOwnStrictVersions = ownStrictVersionConstraints == null;
        try {
            collectAncestorsStrictVersions(incomingEdges);
            for (DependencyState dependencyState : dependencies(resolutionFilter)) {
                PendingDependenciesVisitor.PendingState pendingState = pendingDepsVisitor.maybeAddAsPendingDependency(this, dependencyState);
                if (dependencyState.getDependency().isConstraint()) {
                    registerActivatingConstraint(dependencyState);
                }
                if (!pendingState.isPending()) {
                    createAndLinkEdgeState(dependencyState, discoveredEdges, resolutionFilter, pendingState == PendingDependenciesVisitor.PendingState.NOT_PENDING_ACTIVATING);
                }
                if (shouldComputeOwnStrictVersions) {
                    strictVersionsSet = maybeCollectStrictVersions(strictVersionsSet, dependencyState);
                }
            }
            previousTraversalExclusions = resolutionFilter;
        } finally {
            // If there are 'pending' dependencies that share a target with any of these outgoing edges,
            // then reset the state of the node that owns those dependencies.
            // This way, all edges of the node will be re-processed.
            pendingDepsVisitor.complete();
            if (shouldComputeOwnStrictVersions) {
                storeOwnStrictVersions(strictVersionsSet);
            }
        }
    }

    private void registerActivatingConstraint(DependencyState dependencyState) {
        if (potentiallyActivatedConstraints == null) {
            potentiallyActivatedConstraints = LinkedHashMultimap.create();
        }
        potentiallyActivatedConstraints.put(dependencyState.getModuleIdentifier(), dependencyState);
    }

    private List<? extends DependencyMetadata> dependencies() {
        if (dependenciesMayChange) {
            cachedDependencyStates = null;
            cachedFilteredDependencyStates = null;
        }
        List<? extends DependencyMetadata> dependencies = getAllDependencies();
        if (transitiveEdgeCount == 0 && metadata.isExternalVariant()) {
            // there must be a single dependency state because this variant is an "available-at"
            // variant and here we are in the case the "including" component said that transitive
            // should be false so we need to arbitrarily carry that onto the dependency metadata
            assert dependencies.size() == 1;
            dependencies = Collections.singletonList(makeNonTransitive(dependencies.get(0)));
        }
        doesNotHaveDependencies = dependencies.isEmpty();
        return dependencies;
    }

    protected List<? extends DependencyMetadata> getAllDependencies() {
        return metadata.getDependencies();
    }

    private static DependencyMetadata makeNonTransitive(DependencyMetadata dependencyMetadata) {
        return new NonTransitiveVariantDependencyMetadata(dependencyMetadata);
    }

    private List<DependencyState> dependencies(ExcludeSpec spec) {
        List<? extends DependencyMetadata> dependencies = dependencies();
        if (cachedDependencyStates == null) {
            cachedDependencyStates = cacheDependencyStates(dependencies);
        }
        if (cachedFilteredDependencyStates == null) {
            cachedFilteredDependencyStates = cacheFilteredDependencyStates(spec, cachedDependencyStates);
        }
        return cachedFilteredDependencyStates;
    }

    private List<DependencyState> cacheFilteredDependencyStates(ExcludeSpec spec, List<DependencyState> from) {
        if (from.isEmpty()) {
            return from;
        }
        List<DependencyState> tmp = Lists.newArrayListWithCapacity(from.size());
        for (DependencyState dependencyState : from) {
            if (isExcluded(spec, dependencyState)) {
                continue;
            }
            dependencyState = maybeSubstitute(dependencyState, resolveState.getDependencySubstitutionApplicator());

            if (!isExcluded(spec, dependencyState)) {
                tmp.add(dependencyState);
            }
        }
        return tmp;
    }

    private List<DependencyState> cacheDependencyStates(List<? extends DependencyMetadata> dependencies) {
        if (dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        List<DependencyState> tmp = Lists.newArrayListWithCapacity(dependencies.size());
        for (DependencyMetadata dependency : dependencies) {
            tmp.add(cachedDependencyStateFor(dependency));
        }
        return tmp;
    }

    private DependencyState cachedDependencyStateFor(DependencyMetadata md) {
        return dependencyStateCache.computeIfAbsent(md, this::createDependencyState);
    }

    private void createAndLinkEdgeState(DependencyState dependencyState, Collection<EdgeState> discoveredEdges, ExcludeSpec resolutionFilter, boolean deferSelection) {
        EdgeState dependencyEdge = edgesCache.computeIfAbsent(dependencyState, ds -> new EdgeState(this, ds, resolutionFilter, resolveState));
        dependencyEdge.computeSelector(); // the selector changes, if the 'versionProvidedByAncestors' state changes
        outgoingEdges.add(dependencyEdge);
        dependencyEdge.markUsed();
        discoveredEdges.add(dependencyEdge);
        dependencyEdge.getSelector().use(deferSelection);
    }

    /**
     * Iterate over the dependencies originating in this node, adding only the constraints listed
     * in upcomingNoLongerPendingConstraints
     */
    private void visitAdditionalConstraints(Collection<EdgeState> discoveredEdges) {
        if (potentiallyActivatedConstraints == null) {
            return;
        }
        for (ModuleIdentifier module : upcomingNoLongerPendingConstraints) {
            Collection<DependencyState> dependencyStates = potentiallyActivatedConstraints.get(module);
            if (!dependencyStates.isEmpty()) {
                for (DependencyState dependencyState : dependencyStates) {
                    dependencyState = maybeSubstitute(dependencyState, resolveState.getDependencySubstitutionApplicator());
                    createAndLinkEdgeState(dependencyState, discoveredEdges, previousTraversalExclusions, false);
                }
            }
        }
        upcomingNoLongerPendingConstraints = null;
    }

    /**
     * If a component declares that it belongs to a platform, we add an edge to the platform.
     *
     * @param discoveredEdges the collection of edges for this component
     */
    private void visitOwners(Collection<EdgeState> discoveredEdges) {
        List<? extends VirtualComponentIdentifier> owners = component.getMetadata().getPlatformOwners();
        if (!owners.isEmpty()) {
            PendingDependenciesVisitor visitor = resolveState.newPendingDependenciesVisitor();
            for (VirtualComponentIdentifier owner : owners) {
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
        ComponentGraphResolveState state = potentialEdge.state;
        VirtualPlatformState virtualPlatformState = null;
        if (state == null || state instanceof LenientPlatformGraphResolveState) {
            virtualPlatformState = potentialEdge.component.getModule().getPlatformState();
            virtualPlatformState.participatingModule(component.getModule());
        }
        if (state == null) {
            // the platform doesn't exist, so we're building a lenient one
            state = LenientPlatformGraphResolveState.of(resolveState.getIdGenerator(), platformComponentIdentifier, potentialEdge.toModuleVersionId, virtualPlatformState, this, resolveState);
            potentialEdge.component.setState(state, ComponentGraphSpecificResolveState.EMPTY_STATE);
            // And now let's make sure we do not have another version of that virtual platform missing its metadata
            potentialEdge.component.getModule().maybeCreateVirtualMetadata(resolveState);
        }
        if (virtualEdges == null) {
            virtualEdges = Lists.newArrayList();
        }
        EdgeState edge = potentialEdge.edge;
        virtualEdges.add(edge);
        edge.markUsed();
        discoveredEdges.add(edge);
        edge.getSelector().use(false);
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
            // This caching works because our substitutionResult are cached themselves
            return dependencyState.withSubstitution(substitutionResult, result -> {
                ArtifactSelectionDetailsInternal artifactSelectionDetails = details.getArtifactSelectionDetails();
                if (artifactSelectionDetails.isUpdated()) {
                    return dependencyState.withTargetAndArtifacts(details.getTarget(), artifactSelectionDetails.getTargetSelectors(), details.getRuleDescriptors());
                }
                return dependencyState.withTarget(details.getTarget(), details.getRuleDescriptors());
            });
        }
        return dependencyState;
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
        ModuleIdentifier targetModuleId = dependencyState.getModuleIdentifier();
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
            resolveState.onMoreSelected(this);
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount++;
            }
        }
    }

    void removeIncomingEdge(EdgeState dependencyEdge) {
        if (incomingEdges.remove(dependencyEdge)) {
            incomingHash -= dependencyEdge.hashCode();
            if (dependencyEdge.isTransitive()) {
                transitiveEdgeCount--;
            }
            resolveState.onFewerSelected(this);
        }
    }

    @Override
    public boolean isSelected() {
        return !incomingEdges.isEmpty();
    }

    public void evict() {
        evicted = true;
    }

    public boolean isEvicted() {
        return evicted;
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
            cachedNodeExclusions = moduleExclusions.excludeAny(metadata.getExcludes());
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
            } else if (isConstraint(dependencyEdge)) {
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
        } else if (isConstraint(dependencyEdge)) {
            exclusions = dependencyEdge.getEdgeExclusions();
        }
        if (exclusions == null) {
            exclusions = moduleExclusions.nothing();
        }
        return joinEdgeAndNodeExclusionsThenCacheResult(nodeExclusions, exclusions, 1);
    }

    private static boolean isConstraint(EdgeState dependencyEdge) {
        return dependencyEdge.getDependencyMetadata().isConstraint();
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

    private void collectOwnStrictVersions() {
        List<DependencyState> dependencies = dependencies(computeModuleResolutionFilter(incomingEdges));
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
                    constraintsSet = Sets.newHashSet();
                }
                constraintsSet.add(selector.getModuleIdentifier());
            }
        }
        return constraintsSet;
    }

    private void storeOwnStrictVersions(@Nullable Set<ModuleIdentifier> constraintsSet) {
        if (constraintsSet == null) {
            ownStrictVersionConstraints = StrictVersionConstraints.EMPTY;
        } else {
            ownStrictVersionConstraints = StrictVersionConstraints.of(ImmutableSet.copyOf(constraintsSet));
        }
    }

    /**
     * This methods computes the intersection of ancestors' strict versions coming in from different edges.
     * This is, because only if all paths to this node provides a strict version constraint for a module,
     * {@link #versionProvidedByAncestors(DependencyState)} is true for that module.
     *
     * The result of this method is stored in the 'ancestorsStrictVersionConstraints' field for consumption by downstream nodes.
     *
     * Since the most common case it that there is only one incoming edge, this case is handled first and, if possible,
     * the method returns early.
     */
    private void collectAncestorsStrictVersions(List<EdgeState> incomingEdges) {
        if (incomingEdges.isEmpty()) {
            ancestorsStrictVersionConstraints = StrictVersionConstraints.EMPTY;
            return;
        }

        if (incomingEdges.size() == 1) {
            collectAncestorsStrictVersionsSingleEdge(incomingEdges);
            return;
        }

        collectAncestorsStrictVersionsMultiEdges(incomingEdges);
    }

    private void collectAncestorsStrictVersionsMultiEdges(List<EdgeState> incomingEdges) {
        StrictVersionConstraints constraints = null;
        for (EdgeState dependencyEdge : incomingEdges) {
            StrictVersionConstraints parentStrictVersionConstraints = notNull(dependencyEdge.getFrom().ownStrictVersionConstraints);
            StrictVersionConstraints parentAncestorsStrictVersionConstraints = notNull(dependencyEdge.getFrom().ancestorsStrictVersionConstraints);
            StrictVersionConstraints parentEndorsedStrictVersionConstraints = getEndorsedStrictVersions(dependencyEdge);
            if (constraints == null) {
                constraints = parentStrictVersionConstraints
                    .union(parentAncestorsStrictVersionConstraints)
                    .union(parentEndorsedStrictVersionConstraints);
            } else {
                constraints = constraints.intersect(
                    parentStrictVersionConstraints
                        .union(parentAncestorsStrictVersionConstraints)
                        .union(parentEndorsedStrictVersionConstraints)
                );
            }
            if (constraints == StrictVersionConstraints.EMPTY) {
                ancestorsStrictVersionConstraints = constraints;
                return;
            }
        }
        ancestorsStrictVersionConstraints = constraints;
    }

    private void collectAncestorsStrictVersionsSingleEdge(List<EdgeState> incomingEdges) {
        EdgeState dependencyEdge = incomingEdges.get(0);
        StrictVersionConstraints parentStrictVersionConstraints = notNull(dependencyEdge.getFrom().ownStrictVersionConstraints);
        StrictVersionConstraints parentAncestorsStrictVersionConstraints = notNull(dependencyEdge.getFrom().ancestorsStrictVersionConstraints);
        StrictVersionConstraints parentEndorsedStrictVersionConstraints = getEndorsedStrictVersions(dependencyEdge);
        ancestorsStrictVersionConstraints = parentStrictVersionConstraints
            .union(parentAncestorsStrictVersionConstraints)
            .union(parentEndorsedStrictVersionConstraints);
    }

    private static StrictVersionConstraints notNull(@Nullable StrictVersionConstraints strictVersionConstraints) {
        return strictVersionConstraints == null ? StrictVersionConstraints.EMPTY : strictVersionConstraints;
    }

    private StrictVersionConstraints getEndorsedStrictVersions(EdgeState incomingEdge) {
        if (incomingEdge.getFrom().endorsesStrictVersionsFrom == null) {
            return StrictVersionConstraints.EMPTY;
        }

        boolean filterOwn = false;
        StrictVersionConstraints singleStrictVersionConstraints = StrictVersionConstraints.EMPTY;
        Set<ModuleIdentifier> collectedConstraints = null;
        for (EdgeState edgeState : incomingEdge.getFrom().endorsesStrictVersionsFrom) {
            if (edgeState == incomingEdge) {
                // These are my own constraints. I can not treat them as inherited,
                // because that assumes that they are defined in another node as well and might be ignored.
                filterOwn = true;
                continue;
            }
            ComponentState targetComponent = edgeState.getTargetComponent();
            if (targetComponent != null) { // may be null if the build is about to fail
                for (NodeState sourceNode : targetComponent.getNodes()) {
                    if (sourceNode.ownStrictVersionConstraints == null) {
                        // node's dependencies were not yet visited
                        sourceNode.collectOwnStrictVersions();
                    }
                    if (singleStrictVersionConstraints.isEmpty()) {
                        singleStrictVersionConstraints = sourceNode.ownStrictVersionConstraints;
                    } else {
                        if (collectedConstraints == null) {
                            collectedConstraints = Sets.newHashSet();
                            collectedConstraints.addAll(singleStrictVersionConstraints.getModules());
                        }
                        collectedConstraints.addAll(sourceNode.ownStrictVersionConstraints.getModules());
                    }
                }
            }
        }

        if (filterOwn) {
            Set<ModuleIdentifier> resultSet;
            if (collectedConstraints != null) {
                resultSet = collectedConstraints;
            } else {
                resultSet = singleStrictVersionConstraints.getModules();
            }
            if (ownStrictVersionConstraints == null) {
                collectOwnStrictVersions();
            }
            for (ModuleIdentifier ownConstraint : ownStrictVersionConstraints.getModules()) {
                if (resultSet.contains(ownConstraint)) {
                    if (collectedConstraints == null) {
                        collectedConstraints = Sets.newHashSet();
                        collectedConstraints.addAll(singleStrictVersionConstraints.getModules());
                    }
                    collectedConstraints.remove(ownConstraint);
                }
            }
        }

        if (collectedConstraints != null) {
            return StrictVersionConstraints.of(collectedConstraints);
        } else {
            return singleStrictVersionConstraints;
        }
    }

    void collectEndorsedStrictVersions(List<EdgeState> dependencies) {
        if (endorsesStrictVersionsFrom != null) {
            // we are revisiting this node
            endorsesStrictVersionsFrom.clear();
        }
        for (EdgeState edgeState : dependencies) {
            if (!DependencyGraphBuilder.ENDORSE_STRICT_VERSIONS_DEPENDENCY_SPEC.isSatisfiedBy(edgeState)) {
                continue;
            }
            if (endorsesStrictVersionsFrom == null) {
                endorsesStrictVersionsFrom = Lists.newArrayList();
            }
            endorsesStrictVersionsFrom.add(edgeState);
        }
    }

    boolean versionProvidedByAncestors(DependencyState dependencyState) {
        return !dependencyState.isForced() && ancestorsStrictVersionConstraints != null && ancestorsStrictVersionConstraints.contains(dependencyState.getModuleIdentifier());
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
            for (EdgeState outgoingDependency : outgoingEdges) {
                outgoingDependency.markUnused();
                ComponentState targetComponent = outgoingDependency.getTargetComponent();
                if (targetComponent == component) {
                    // if the same component depends on itself: do not attempt to cleanup the same thing several times
                    continue;
                }
                if (targetComponent != null && targetComponent.getModule().isChangingSelection()) {
                    // don't requeue something which is already changing selection
                    continue;
                }
                outgoingDependency.cleanUpOnSourceChange(this);
            }
            outgoingEdges.clear();
        }
        if (virtualEdges != null /*&& !removingOutgoing*/) {
            for (EdgeState outgoingDependency : virtualEdges) {
                outgoingDependency.markUnused();
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release(resolveState.getConflictTracker());
            }
        }
        virtualEdges = null;
        previousTraversalExclusions = null;
        cachedFilteredDependencyStates = null;
        virtualPlatformNeedsRefresh = false;
        removingOutgoingEdges = alreadyRemoving;
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
            singleEdge.restartConnected();
        } else {
            for (EdgeState edge : new ArrayList<>(incomingEdges)) {
                edge.restartConnected();
            }
        }
        // See https://github.com/gradle/gradle/pull/26016#issuecomment-1795491970
        List<EdgeState> incomingEdges = ImmutableList.copyOf(this.incomingEdges);
        clearIncomingEdges();
        for (EdgeState incomingEdge : incomingEdges) {
            NodeState from = incomingEdge.getFrom();
            from.removeOutgoingEdge(incomingEdge);
        }
    }

    private void clearIncomingEdges() {
        incomingEdges.clear();
        incomingHash = 0;
        transitiveEdgeCount = 0;
    }

    public void deselect() {
        removeOutgoingEdges();
        reselectEndorsingNode();
    }

    private void reselectEndorsingNode() {
        if (incomingEdges.size() == 1) {
            EdgeState singleEdge = incomingEdges.get(0);
            NodeState from = singleEdge.getFrom();
            if (singleEdge.getDependencyState().getDependency().isEndorsingStrictVersions()) {
                // pass my own component because we are already in the process of re-selecting it
                from.reselect();
            }
        } else {
            for (EdgeState incoming : Lists.newArrayList(incomingEdges)) {
                if (incoming.getDependencyState().getDependency().isEndorsingStrictVersions()) {
                    // pass my own component because we are already in the process of re-selecting it
                    incoming.getFrom().reselect();
                }
            }
        }
    }

    private void reselect() {
        resolveState.onMoreSelected(this);
        removeOutgoingEdges();
    }

    void prepareForConstraintNoLongerPending(ModuleIdentifier moduleIdentifier) {
        if (upcomingNoLongerPendingConstraints == null) {
            upcomingNoLongerPendingConstraints = Sets.newLinkedHashSet();
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

    public ImmutableAttributesFactory getAttributesFactory() {
        return resolveState.getAttributesFactory();
    }

    /**
     * Invoked when this node is back to being a pending dependency.
     * There may be some incoming edges left at that point, but they must all be coming from constraints.
     */
    public void clearConstraintEdges(PendingDependencies pendingDependencies, NodeState backToPendingSource) {
        if (incomingEdges.isEmpty()) {
            return;
        }
        // Cleaning has to be done on a copied collection because of the recompute happening on selector removal
        List<EdgeState> remainingIncomingEdges = ImmutableList.copyOf(incomingEdges);
        clearIncomingEdges();
        for (EdgeState incomingEdge : remainingIncomingEdges) {
            assert isConstraint(incomingEdge);
            NodeState from = incomingEdge.getFrom();
            if (from != backToPendingSource) {
                // Only remove edges that come from a different node than the source of the dependency going back to pending
                // The edges from the "From" will be removed first
                from.removeOutgoingEdge(incomingEdge);
            }
            pendingDependencies.registerConstraintProvider(from);
        }
    }

    private void removeOutgoingEdge(EdgeState edge) {
        if (!removingOutgoingEdges) {
            // don't try to remove an outgoing edge if we're already doing it
            // because removeOutgoingEdges() will clear all of them so it's not required to do it twice
            // and it can cause a concurrent modification exception
            outgoingEdges.remove(edge);
            edge.markUnused();
            edge.getSelector().release(resolveState.getConflictTracker());
        }
    }

    void forEachCapability(CapabilitiesConflictHandler capabilitiesConflictHandler, Action<? super Capability> action) {
        List<? extends Capability> capabilities = metadata.getCapabilities().getCapabilities();
        // If there's more than one node selected for the same component, we need to add
        // the implicit capability to the list, in order to make sure we can discover conflicts
        // between variants of the same module.
        // We also need to add the implicit capability if it was seen before as an explicit
        // capability in order to detect the conflict between the two.
        // Note that the fact that the implicit capability is not included in other cases
        // is not a bug but a performance optimization.
        if (capabilities.isEmpty() && (component.hasMoreThanOneSelectedNodeUsingVariantAwareResolution() || capabilitiesConflictHandler.hasSeenCapability(component.getImplicitCapability()))) {
            action.execute(component.getImplicitCapability());
        } else {
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

    @Nullable
    public Capability findCapability(String group, String name) {
        Capability onComponent = component.findCapability(group, name);
        if (onComponent != null) {
            return onComponent;
        }
        List<? extends Capability> capabilities = metadata.getCapabilities().getCapabilities();
        if (!capabilities.isEmpty()) { // Not required, but Guava's performance bad for an empty immutable list
            for (Capability capability : capabilities) {
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
        for (Capability capability : metadata.getCapabilities().getCapabilities()) {
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
            edgeState.getSelector().release(resolveState.getConflictTracker());
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
        formatter.node(getSimpleName());
        NodeState from = this;
        int depth = 0;
        do {
            from = getFromNode(from);
            if (from != null) {
                formatter.startChildren();
                formatter.node(from.getSimpleName());
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

    public void updateTransitiveExcludes() {
        cachedModuleResolutionFilter = null;
        if (isSelected()) {
            resolveState.onMoreSelected(this);
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
