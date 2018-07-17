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
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionApplicator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.external.model.ComponentVariant;
import org.gradle.internal.component.external.model.DefaultConfigurationMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.VariantMetadataRules;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.component.model.LocalComponentDependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
    private ModuleExclusion previousTraversalExclusions;
    // In opposite to outgoing edges, virtual edges are for now pretty rare, so they are created lazily
    private List<EdgeState> virtualEdges;

    NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
        this.resultId = resultId;
        this.id = id;
        this.component = component;
        this.resolveState = resolveState;
        this.metaData = md;
        this.isTransitive = metaData.isTransitive();
        component.addConfiguration(this);
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
     * @param pendingDependenciesHandler Handler for pending dependencies.
     */
    public void visitOutgoingDependencies(Collection<EdgeState> discoveredEdges, PendingDependenciesHandler pendingDependenciesHandler) {
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
        boolean hasIncomingEdges = !incomingEdges.isEmpty();
        List<EdgeState> transitiveIncoming = getTransitiveIncomingEdges();

        // Check if there are any transitive incoming edges at all. Don't traverse if not.
        if (transitiveIncoming.isEmpty() && !isRoot()) {
            // If node was previously traversed, need to remove outgoing edges.
            if (previousTraversalExclusions != null) {
                removeOutgoingEdges();
            }
            if (hasIncomingEdges) {
                LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
            } else {
                LOGGER.debug("{} has no incoming edges. ignoring.", this);
            }
            return;
        }

        // Determine the net exclusion for this node, by inspecting all transitive incoming edges
        ModuleExclusion resolutionFilter = getModuleResolutionFilter(transitiveIncoming);

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

        visitDependencies(resolutionFilter, pendingDependenciesHandler, discoveredEdges);
        visitOwners(discoveredEdges);
    }

    /**
     * Iterate over the dependencies originating in this node, adding them either as a 'pending' dependency
     * or adding them to the `discoveredEdges` collection (and `this.outgoingEdges`)
     */
    private void visitDependencies(ModuleExclusion resolutionFilter, PendingDependenciesHandler pendingDependenciesHandler, Collection<EdgeState> discoveredEdges) {
        PendingDependenciesHandler.Visitor pendingDepsVisitor = pendingDependenciesHandler.start();
        try {
            for (DependencyMetadata dependency : metaData.getDependencies()) {
                DependencyState dependencyState = new DependencyState(dependency, resolveState.getComponentSelectorConverter());
                if (isExcluded(resolutionFilter, dependencyState)) {
                    continue;
                }
                dependencyState = maybeSubstitute(dependencyState);
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
            for (ComponentIdentifier owner : owners) {
                if (owner instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier platformId = (ModuleComponentIdentifier) owner;
                    final ModuleComponentSelector cs = DefaultModuleComponentSelector.newSelector(platformId.getModuleIdentifier(), platformId.getVersion());

                    // There are 2 possibilities here:
                    // 1. the "platform" referenced is a real module, in which case we directly add it to the graph
                    // 2. the "platform" is a virtual, constructed thing, in which case we add virtual edges to the graph
                    addPlatformEdges(discoveredEdges, platformId, cs);
                }
            }
        }
    }

    private PotentialEdge potentialEdgeTo(ModuleComponentIdentifier toComponent, ModuleComponentSelector toSelector, ComponentIdentifier owner) {
        DependencyState dependencyState = new DependencyState(new LenientPlatformDependencyMetadata(toSelector, toComponent, owner), resolveState.getComponentSelectorConverter());
        EdgeState edge = new TransientEdge(dependencyState);
        ModuleVersionIdentifier toModuleVersionId = DefaultModuleVersionIdentifier.newId(toSelector.getModuleIdentifier(), toSelector.getVersion());
        ComponentState version = resolveState.getModule(toSelector.getModuleIdentifier()).getVersion(toModuleVersionId, toComponent);
        SelectorState selector = edge.getSelector();
        version.selectedBy(selector);
        // We need to check if the target version exists. For this,
        // we have to try to get metadata for the aligned version. If it's there,
        // it means we can align, otherwise, we must NOT add the edge, or resolution
        // would fail
        ComponentResolveMetadata metadata = version.getMetadata();
        return new PotentialEdge(edge, toModuleVersionId, metadata, version);
    }

    private void addPlatformEdges(Collection<EdgeState> discoveredEdges, ModuleComponentIdentifier platformComponentIdentifier, ModuleComponentSelector platformSelector) {
        PotentialEdge potentialEdge = potentialEdgeTo(platformComponentIdentifier, platformSelector, platformComponentIdentifier);
        ComponentResolveMetadata metadata = potentialEdge.metadata;
        VirtualPlatformState virtualPlatformState = null;
        if (metadata == null || metadata instanceof LenientPlatformResolveMetadata) {
            virtualPlatformState = potentialEdge.component.getModule().getPlatformState();
            virtualPlatformState.participatingModule(component.getModule());
        }
        if (metadata == null) {
            // the platform doesn't exist, so we're building a lenient one
            metadata = new LenientPlatformResolveMetadata(platformComponentIdentifier, potentialEdge.toModuleVersionId, virtualPlatformState);
            potentialEdge.component.setMetadata(metadata);
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
    private DependencyState maybeSubstitute(DependencyState dependencyState) {
        DependencySubstitutionApplicator.SubstitutionResult substitutionResult = resolveState.getDependencySubstitutionApplicator().apply(dependencyState.getDependency());
        if (substitutionResult.hasFailure()) {
            dependencyState.failure = new ModuleVersionResolveException(dependencyState.getRequested(), substitutionResult.getFailure());
            return dependencyState;
        }

        DependencySubstitutionInternal details = substitutionResult.getResult();
        if (details != null && details.isUpdated()) {
            return dependencyState.withTarget(details.getTarget(), details.getSelectionDescription());
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

    private ModuleExclusion getModuleResolutionFilter(List<EdgeState> incomingEdges) {
        ModuleExclusions moduleExclusions = resolveState.getModuleExclusions();
        ModuleExclusion nodeExclusions = moduleExclusions.excludeAny(metaData.getExcludes());
        if (incomingEdges.isEmpty()) {
            return nodeExclusions;
        }
        ModuleExclusion edgeExclusions = incomingEdges.get(0).getExclusions();
        for (int i = 1; i < incomingEdges.size(); i++) {
            EdgeState dependencyEdge = incomingEdges.get(i);
            edgeExclusions = moduleExclusions.union(edgeExclusions, dependencyEdge.getExclusions());
        }
        return moduleExclusions.intersect(edgeExclusions, nodeExclusions);
    }

    private void removeOutgoingEdges() {
        if (!outgoingEdges.isEmpty()) {
            for (EdgeState outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
                outgoingDependency.getSelector().release();
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
            EdgeState singleEdge = incomingEdges.iterator().next();
            singleEdge.restart();
        } else {
            for (EdgeState dependency : new ArrayList<EdgeState>(incomingEdges)) {
                dependency.restart();
            }
        }
        incomingEdges.clear();
    }

    public void deselect() {
        removeOutgoingEdges();
    }

    void resetSelectionState() {
        previousTraversalExclusions = null;
        outgoingEdges.clear();
        virtualEdges = null;
        resolveState.onMoreSelected(this);
    }

    public ImmutableAttributesFactory getAttributesFactory() {
        return resolveState.getAttributesFactory();
    }

    private class LenientPlatformDependencyMetadata implements ModuleDependencyMetadata {
        private final ModuleComponentSelector cs;
        private final ModuleComponentIdentifier componentId;
        private final ComponentIdentifier platformId; // just for reporting

        LenientPlatformDependencyMetadata(ModuleComponentSelector cs, ModuleComponentIdentifier componentId, ComponentIdentifier platformId) {
            this.cs = cs;
            this.componentId = componentId;
            this.platformId = platformId;
        }

        @Override
        public ModuleComponentSelector getSelector() {
            return cs;
        }

        @Override
        public ModuleDependencyMetadata withRequestedVersion(VersionConstraint requestedVersion) {
            return this;
        }

        @Override
        public ModuleDependencyMetadata withReason(String reason) {
            return this;
        }

        @Override
        public List<ConfigurationMetadata> selectConfigurations(ImmutableAttributes consumerAttributes, ComponentResolveMetadata targetComponent, AttributesSchemaInternal consumerSchema) {
            if (targetComponent instanceof LenientPlatformResolveMetadata) {
                LenientPlatformResolveMetadata platformMetadata = (LenientPlatformResolveMetadata) targetComponent;
                return Collections.<ConfigurationMetadata>singletonList(new LenientPlatformConfigurationMetadata(platformMetadata.getPlatformState(), platformId));
            }
            // the target component exists, so we need to fallback to the traditional selection process
            return new LocalComponentDependencyMetadata(componentId, cs, null, ImmutableAttributes.EMPTY, ImmutableAttributes.EMPTY, null, Collections.<IvyArtifactName>emptyList(), Collections.<ExcludeMetadata>emptyList(), false, false, true, false, null).selectConfigurations(consumerAttributes, targetComponent, consumerSchema);
        }

        @Override
        public List<ExcludeMetadata> getExcludes() {
            return Collections.emptyList();
        }

        @Override
        public List<IvyArtifactName> getArtifacts() {
            return Collections.emptyList();
        }

        @Override
        public DependencyMetadata withTarget(ComponentSelector target) {
            return this;
        }

        @Override
        public boolean isChanging() {
            return false;
        }

        @Override
        public boolean isTransitive() {
            return true;
        }

        @Override
        public boolean isPending() {
            return true;
        }

        @Override
        public String getReason() {
            return "belongs to platform " + platformId;
        }

        @Override
        public String toString() {
            return "virtual metadata for " + componentId;
        }

        private class LenientPlatformConfigurationMetadata extends DefaultConfigurationMetadata {

            private final VirtualPlatformState platformState;
            private final ComponentIdentifier platformId;

            public LenientPlatformConfigurationMetadata(VirtualPlatformState platform, ComponentIdentifier platformId) {
                super(componentId, "default", true, false, ImmutableList.of("default"), ImmutableList.<ModuleComponentArtifactMetadata>of(), VariantMetadataRules.noOp(), ImmutableList.<ExcludeMetadata>of(), ImmutableAttributes.EMPTY);
                this.platformState = platform;
                this.platformId = platformId;
            }

            @Override
            public List<? extends DependencyMetadata> getDependencies() {
                List<DependencyMetadata> result = null;
                List<String> candidateVersions = platformState.getCandidateVersions();
                Set<ModuleResolveState> modules = platformState.getParticipatingModules();
                for (ModuleResolveState module : modules) {
                    ComponentState selected = module.getSelected();
                    if (selected != null) {
                        String componentVersion = selected.getId().getVersion();
                        for (String target : candidateVersions) {
                            ModuleComponentIdentifier leafId = DefaultModuleComponentIdentifier.newId(module.getId(), target);
                            ModuleComponentSelector leafSelector = DefaultModuleComponentSelector.newSelector(module.getId(), target);
                            ComponentIdentifier platformId = platformState.getSelectedPlatformId();
                            if (platformId == null) {
                                // Not sure this can happen, unless in error state
                                platformId = this.platformId;
                            }
                            if (!componentVersion.equals(target)) {
                                // We will only add dependencies to the leaves if there is such a published module
                                PotentialEdge potentialEdge = potentialEdgeTo(leafId, leafSelector, platformId);
                                if (potentialEdge.metadata != null) {
                                    result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId);
                                    break;
                                }
                            } else {
                                // at this point we know the component exists
                                result = registerPlatformEdge(result, modules, leafId, leafSelector, platformId);
                                break;
                            }
                        }
                    }
                }
                return result == null ? Collections.<DependencyMetadata>emptyList() : result;
            }

            private List<DependencyMetadata> registerPlatformEdge(List<DependencyMetadata> result, Set<ModuleResolveState> modules, ModuleComponentIdentifier leafId, ModuleComponentSelector leafSelector, ComponentIdentifier platformId) {
                if (result == null) {
                    result = Lists.newArrayListWithExpectedSize(modules.size());
                }
                result.add(new LenientPlatformDependencyMetadata(
                    leafSelector,
                    leafId,
                    platformId
                ));
                return result;
            }
        }
    }

    private static class LenientPlatformResolveMetadata implements ModuleComponentResolveMetadata {

        private final ModuleComponentIdentifier moduleComponentIdentifier;
        private final ModuleVersionIdentifier moduleVersionIdentifier;
        private final VirtualPlatformState platformState;

        private LenientPlatformResolveMetadata(ModuleComponentIdentifier moduleComponentIdentifier, ModuleVersionIdentifier moduleVersionIdentifier, VirtualPlatformState platformState) {
            this.moduleComponentIdentifier = moduleComponentIdentifier;
            this.moduleVersionIdentifier = moduleVersionIdentifier;
            this.platformState = platformState;
        }

        @Override
        public ModuleComponentIdentifier getId() {
            return moduleComponentIdentifier;
        }

        @Override
        public ModuleVersionIdentifier getModuleVersionId() {
            return moduleVersionIdentifier;
        }

        @Override
        public ModuleSource getSource() {
            return null;
        }

        @Override
        public AttributesSchemaInternal getAttributesSchema() {
            return null;
        }

        @Override
        public ModuleComponentResolveMetadata withSource(ModuleSource source) {
            return this;
        }

        @Override
        public Set<String> getConfigurationNames() {
            return null;
        }

        @Nullable
        @Override
        public ConfigurationMetadata getConfiguration(String name) {
            return null;
        }

        @Override
        public Optional<ImmutableList<? extends ConfigurationMetadata>> getVariantsForGraphTraversal() {
            return Optional.absent();
        }

        @Override
        public boolean isMissing() {
            return false;
        }

        @Override
        public boolean isChanging() {
            return false;
        }

        @Override
        public String getStatus() {
            return null;
        }

        @Override
        public List<String> getStatusScheme() {
            return null;
        }

        @Override
        public ImmutableList<? extends ComponentIdentifier> getPlatformOwners() {
            return ImmutableList.of();
        }

        @Override
        public MutableModuleComponentResolveMetadata asMutable() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ModuleComponentArtifactMetadata artifact(String type, @Nullable String extension, @Nullable String classifier) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HashValue getOriginalContentHash() {
            return null;
        }

        @Override
        public ImmutableList<? extends ComponentVariant> getVariants() {
            return ImmutableList.of();
        }

        @Override
        public ImmutableAttributesFactory getAttributesFactory() {
            return null;
        }

        @Override
        public AttributeContainer getAttributes() {
            return ImmutableAttributes.EMPTY;
        }

        VirtualPlatformState getPlatformState() {
            return platformState;
        }
    }

    /**
     * Represents an edge in the graph which is added only as an implementation detail. This
     * is used for backlinks, from a component to its owner.
     */
    private class TransientEdge extends EdgeState {
        TransientEdge(DependencyState dependencyState) {
            super(NodeState.this, dependencyState, NodeState.this.previousTraversalExclusions, NodeState.this.resolveState);
        }
    }

    /**
     * This class wraps knowledge about a potential edge to a component. It's called potential,
     * because when the edge is created we don't know if the target component exists, and, since
     * the edge is created internally by the engine, we don't want to fail if the target component
     * doesn't exist. This means that the edge would effectively be added if, and only if, the
     * target component exists. Checking if it does exist is currently done by fetching metadata,
     * but we could have a cheaper strategy (HEAD request, ...).
     */
    private static class PotentialEdge {
        private final EdgeState edge;
        private final ModuleVersionIdentifier toModuleVersionId;
        private final ComponentResolveMetadata metadata;
        private final ComponentState component;

        PotentialEdge(EdgeState edge, ModuleVersionIdentifier toModuleVersionId, ComponentResolveMetadata metadata, ComponentState component) {
            this.edge = edge;
            this.toModuleVersionId = toModuleVersionId;
            this.metadata = metadata;
            this.component = component;
        }
    }
}
