/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ResolveContext;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ComponentResolutionState;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusion;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.CandidateModule;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictHandler;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.ConflictResolutionResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.conflicts.PotentialConflict;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.Exclude;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentIdResolveResult;
import org.gradle.internal.resolve.result.ComponentResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.DefaultBuildableComponentResolveResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final ConflictHandler conflictHandler;
    private final Spec<? super DependencyMetadata> edgeFilter;
    private final ResolveContextToComponentResolver moduleResolver;
    private final DependencyToComponentIdResolver idResolver;
    private final ComponentMetaDataResolver metaDataResolver;
    private final AttributesSchemaInternal attributesSchema;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final ModuleExclusions moduleExclusions;
    private final BuildOperationExecutor buildOperationExecutor;

    public DependencyGraphBuilder(DependencyToComponentIdResolver componentIdResolver, ComponentMetaDataResolver componentMetaDataResolver,
                                  ResolveContextToComponentResolver resolveContextToComponentResolver,
                                  ConflictHandler conflictHandler, Spec<? super DependencyMetadata> edgeFilter,
                                  AttributesSchemaInternal attributesSchema,
                                  ImmutableModuleIdentifierFactory moduleIdentifierFactory, ModuleExclusions moduleExclusions,
                                  BuildOperationExecutor buildOperationExecutor) {
        this.idResolver = componentIdResolver;
        this.metaDataResolver = componentMetaDataResolver;
        this.moduleResolver = resolveContextToComponentResolver;
        this.conflictHandler = conflictHandler;
        this.edgeFilter = edgeFilter;
        this.attributesSchema = attributesSchema;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.moduleExclusions = moduleExclusions;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void resolve(final ResolveContext resolveContext, final DependencyGraphVisitor modelVisitor) {

        IdGenerator<Long> idGenerator = new LongIdGenerator();
        DefaultBuildableComponentResolveResult rootModule = new DefaultBuildableComponentResolveResult();
        moduleResolver.resolve(resolveContext, rootModule);

        final ResolveState resolveState = new ResolveState(idGenerator, rootModule, resolveContext.getName(), idResolver, metaDataResolver, edgeFilter, attributesSchema, moduleIdentifierFactory, moduleExclusions);
        conflictHandler.registerResolver(new DirectDependencyForcingResolver(resolveState.root.component));

        traverseGraph(resolveState);

        resolveState.root.component.setSelectionReason(VersionSelectionReasons.ROOT);

        assembleResult(resolveState, modelVisitor);

    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(final ResolveState resolveState) {
        resolveState.onMoreSelected(resolveState.root);
        final List<EdgeState> dependencies = Lists.newArrayList();
        final List<EdgeState> dependenciesMissingLocalMetadata = Lists.newArrayList();
        final Map<ModuleVersionIdentifier, ComponentIdentifier> componentIdentifierCache = Maps.newHashMap();

        while (resolveState.peek() != null || conflictHandler.hasConflicts()) {
            if (resolveState.peek() != null) {
                final NodeState node = resolveState.pop();
                LOGGER.debug("Visiting configuration {}.", node);

                // Calculate the outgoing edges of this configuration
                dependencies.clear();
                dependenciesMissingLocalMetadata.clear();
                node.visitOutgoingDependencies(dependencies);

                resolveEdges(node, dependencies, dependenciesMissingLocalMetadata, resolveState, componentIdentifierCache);

            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                conflictHandler.resolveNextConflict(new Action<ConflictResolutionResult>() {
                    public void execute(final ConflictResolutionResult result) {
                        result.getConflict().withParticipatingModules(new Action<ModuleIdentifier>() {
                            public void execute(ModuleIdentifier moduleIdentifier) {
                                ComponentState selected = result.getSelected();
                                // Restart each configuration. For the evicted configuration, this means moving incoming dependencies across to the
                                // matching selected configuration. For the select configuration, this mean traversing its dependencies.
                                resolveState.getModule(moduleIdentifier).restart(selected);
                            }
                        });
                    }
                });
            }

        }
    }

    private void performSelection(final ResolveState resolveState, ComponentState moduleRevision) {
        ModuleIdentifier moduleId = moduleRevision.id.getModule();

        // Check for a new conflict
        if (moduleRevision.state == ModuleState.New) {
            ModuleResolveState module = resolveState.getModule(moduleId);
            // A new module revision. Check for conflict
            PotentialConflict c = conflictHandler.registerModule(module);
            if (!c.conflictExists()) {
                // No conflict. Select it for now
                LOGGER.debug("Selecting new module version {}", moduleRevision);
                module.select(moduleRevision);
            } else {
                // We have a conflict
                LOGGER.debug("Found new conflicting module version {}", moduleRevision);

                // Deselect the currently selected version, and remove all outgoing edges from the version
                // This will propagate through the graph and prune configurations that are no longer required
                // For each module participating in the conflict (many times there is only one participating module that has multiple versions)
                c.withParticipatingModules(new Action<ModuleIdentifier>() {
                    public void execute(ModuleIdentifier module) {
                        ComponentState previouslySelected = resolveState.getModule(module).clearSelection();
                        if (previouslySelected != null) {
                            for (NodeState configuration : previouslySelected.nodes) {
                                configuration.deselect();
                            }
                        }
                    }
                });
            }
        }
    }

    private void resolveEdges(final NodeState node,
                              final List<EdgeState> dependencies,
                              final List<EdgeState> dependenciesMissingMetadataLocally,
                              final ResolveState resolveState,
                              final Map<ModuleVersionIdentifier, ComponentIdentifier> componentIdentifierCache) {
        if (dependencies.isEmpty()) {
            return;
        }
        performSelectionSerially(dependencies, resolveState);
        computePreemptiveDownloadList(dependencies, dependenciesMissingMetadataLocally, componentIdentifierCache);
        downloadMetadataConcurrently(node, dependenciesMissingMetadataLocally);
        attachToTargetRevisionsSerially(dependencies);

    }

    private void attachToTargetRevisionsSerially(List<EdgeState> dependencies) {
        // the following only needs to be done serially to preserve ordering of dependencies in the graph: we have visited the edges
        // but we still didn't add the result to the queue. Doing it from resolve threads would result in non-reproducible graphs, where
        // edges could be added in different order. To avoid this, the addition of new edges is done serially.
        for (EdgeState dependency : dependencies) {
            if (dependency.targetModuleRevision != null && dependency.targetModuleRevision.state == ModuleState.Selected) {
                dependency.attachToTargetConfigurations();
            }
        }
    }

    private void downloadMetadataConcurrently(NodeState node, final List<EdgeState> dependencies) {
        if (dependencies.isEmpty()) {
            return;
        }
        LOGGER.debug("Submitting {} metadata files to resolve in parallel for {}", dependencies.size(), node);
        buildOperationExecutor.runAll(new Action<BuildOperationQueue<RunnableBuildOperation>>() {
            @Override
            public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
                for (final EdgeState dependency : dependencies) {
                    buildOperationQueue.add(new DownloadMetadataOperation(dependency.targetModuleRevision));
                }
            }
        });
    }

    private void performSelectionSerially(List<EdgeState> dependencies, ResolveState resolveState) {
        for (EdgeState dependency : dependencies) {
            ComponentState moduleRevision = dependency.resolveModuleRevisionId();
            if (moduleRevision != null) {
                performSelection(resolveState, moduleRevision);
            }
        }
    }

    /**
     * Prepares the resolution of edges, either serially or concurrently. It uses a simple heuristic to determine
     * if we should perform concurrent resolution, based on the the number of edges, and whether they have unresolved
     * metadata. Determining this requires calls to `resolveModuleRevisionId`, which will *not* trigger metadata download.
     *
     * @param dependencies the dependencies to be resolved
     * @param dependenciesToBeResolvedInParallel output, edges which will need parallel metadata download
     */
    private void computePreemptiveDownloadList(List<EdgeState> dependencies, List<EdgeState> dependenciesToBeResolvedInParallel, Map<ModuleVersionIdentifier, ComponentIdentifier> componentIdentifierCache) {
        for (EdgeState dependency : dependencies) {
            ComponentState state = dependency.targetModuleRevision;
            if (state != null && !state.fastResolve() && performPreemptiveDownload(state.state)) {
                if (!metaDataResolver.isFetchingMetadataCheap(toComponentId(state.getId(), componentIdentifierCache))) {
                    dependenciesToBeResolvedInParallel.add(dependency);
                }
            }
        }
        if (dependenciesToBeResolvedInParallel.size() == 1) {
            // don't bother doing anything in parallel if there's a single edge
            dependenciesToBeResolvedInParallel.clear();
        }
    }

    private static ComponentIdentifier toComponentId(ModuleVersionIdentifier id, Map<ModuleVersionIdentifier, ComponentIdentifier> componentIdentifierCache) {
        ComponentIdentifier identifier = componentIdentifierCache.get(id);
        if (identifier == null) {
            identifier = DefaultModuleComponentIdentifier.newId(id);
            componentIdentifierCache.put(id, identifier);
        }
        return identifier;
    }

    private static boolean performPreemptiveDownload(ModuleState state) {
        return state == ModuleState.Selected;
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, DependencyGraphVisitor visitor) {
        visitor.start(resolveState.root);

        // Visit the selectors
        for (DependencyGraphSelector selector : resolveState.getSelectors()) {
            visitor.visitSelector(selector);
        }

        // Visit the nodes prior to visiting the edges
        for (NodeState nodeState : resolveState.getNodes()) {
            if (nodeState.isSelected()) {
                visitor.visitNode(nodeState);
            }
        }

        // Collect the components to sort in consumer-first order
        List<ComponentState> queue = new ArrayList<ComponentState>();
        for (ModuleResolveState module : resolveState.getModules()) {
            if (module.getSelected() != null) {
                queue.add(module.getSelected());
            }
        }

        // Visit the edges after sorting the components in consumer-first order
        while (!queue.isEmpty()) {
            ComponentState component = queue.get(0);
            if (component.getVisitState() == VisitState.NotSeen) {
                component.setVisitState(VisitState.Visting);
                int pos = 0;
                for (NodeState node : component.getNodes()) {
                    if (!node.isSelected()) {
                        continue;
                    }
                    for (EdgeState edge : node.getIncomingEdges()) {
                        ComponentState owner = edge.getFrom().getOwner();
                        if (owner.getVisitState() == VisitState.NotSeen) {
                            queue.add(pos, owner);
                            pos++;
                        } // else, already visited or currently visiting (which means a cycle), skip
                    }
                }
                if (pos == 0) {
                    // have visited all consumers, so visit this node
                    component.setVisitState(VisitState.Visited);
                    queue.remove(0);
                    for (NodeState node : component.getNodes()) {
                        if (node.isSelected()) {
                            visitor.visitEdges(node);
                        }
                    }
                }
            } else if (component.getVisitState() == VisitState.Visting) {
                // have visited all consumers, so visit this node
                component.setVisitState(VisitState.Visited);
                queue.remove(0);
                for (NodeState node : component.getNodes()) {
                    if (node.isSelected()) {
                        visitor.visitEdges(node);
                    }
                }
            } else {
                // else, already visited previously, skip
                queue.remove(0);
            }
        }

        visitor.finish(resolveState.root);
    }

    /**
     * Represents the edges in the dependency graph.
     */
    private static class EdgeState implements DependencyGraphEdge {
        public final NodeState from;
        public final SelectorState selector;

        private final DependencyMetadata dependencyMetadata;
        private final ResolveState resolveState;
        private final ModuleExclusion moduleExclusion;
        private final Set<NodeState> targetNodes = new LinkedHashSet<NodeState>();

        private ComponentState targetModuleRevision;
        private ModuleVersionResolveException targetNodeSelectionFailure;

        EdgeState(NodeState from, DependencyMetadata dependencyMetadata, ModuleExclusion moduleExclusion, ResolveState resolveState) {
            this.from = from;
            this.dependencyMetadata = dependencyMetadata;
            this.moduleExclusion = moduleExclusion;
            this.resolveState = resolveState;
            this.selector = resolveState.getSelector(dependencyMetadata);
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
        public DependencyGraphSelector getSelector() {
            return selector;
        }

        /**
         * @return The resolved module version
         */
        public ComponentState resolveModuleRevisionId() {
            if (targetModuleRevision == null) {
                targetModuleRevision = selector.resolveModuleRevisionId();
                selector.getSelectedModule().addUnattachedDependency(this);
            }
            return targetModuleRevision;
        }

        public boolean isTransitive() {
            return from.isTransitive() && dependencyMetadata.isTransitive();
        }

        public void attachToTargetConfigurations() {
            if (targetModuleRevision.state != ModuleState.Selected) {
                return;
            }
            calculateTargetConfigurations();
            for (NodeState targetConfiguration : targetNodes) {
                targetConfiguration.addIncomingEdge(this);
            }
            if (!targetNodes.isEmpty()) {
                selector.getSelectedModule().removeUnattachedDependency(this);
            }
        }

        public void removeFromTargetConfigurations() {
            for (NodeState targetConfiguration : targetNodes) {
                targetConfiguration.removeIncomingEdge(this);
            }
            targetNodes.clear();
            targetNodeSelectionFailure = null;
            if (targetModuleRevision != null) {
                selector.getSelectedModule().removeUnattachedDependency(this);
            }
        }

        public void restart(ComponentState selected) {
            removeFromTargetConfigurations();
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetNodes.clear();
            targetNodeSelectionFailure = null;
            ComponentResolveMetadata targetModuleVersion = targetModuleRevision.getMetaData();
            if (targetModuleVersion == null) {
                // Broken version
                return;
            }

            Set<ConfigurationMetadata> targetConfigurations;
            try {
                targetConfigurations = dependencyMetadata.selectConfigurations(from.component.metaData, from.metaData, targetModuleVersion, resolveState.getAttributesSchema());
            } catch (Throwable t) {
//                 Broken selector
                targetNodeSelectionFailure = new ModuleVersionResolveException(dependencyMetadata.getSelector(), t);
                return;
            }
            for (ConfigurationMetadata targetConfiguration : targetConfigurations) {
                NodeState targetNodeState = resolveState.getNode(targetModuleRevision, targetConfiguration);
                this.targetNodes.add(targetNodeState);
            }
        }

        public ModuleExclusion toExclusions(DependencyMetadata md, ConfigurationMetadata from) {
            List<Exclude> excludes = md.getExcludes(from.getHierarchy());
            if (excludes.isEmpty()) {
                return ModuleExclusions.excludeNone();
            }
            return resolveState.moduleExclusions.excludeAny(excludes);
        }

        @Override
        public ModuleExclusion getExclusions(ModuleExclusions moduleExclusions) {
            ModuleExclusion edgeExclusions = toExclusions(dependencyMetadata, from.metaData);
            return resolveState.moduleExclusions.intersect(edgeExclusions, moduleExclusion);
        }

        @Override
        public ComponentSelector getRequested() {
            return dependencyMetadata.getSelector();
        }

        @Override
        public ModuleVersionSelector getRequestedModuleVersion() {
            return dependencyMetadata.getRequested();
        }

        @Override
        public ModuleVersionResolveException getFailure() {
            if (targetNodeSelectionFailure != null) {
                return targetNodeSelectionFailure;
            }
            return selector.getFailure();
        }

        @Override
        public Long getSelected() {
            return selector.getSelected().getResultId();
        }

        @Override
        public ComponentSelectionReason getReason() {
            return selector.getSelectionReason();
        }

        @Override
        public ModuleDependency getModuleDependency() {
            if (dependencyMetadata instanceof DslOriginDependencyMetadata) {
                return ((DslOriginDependencyMetadata) dependencyMetadata).getSource();
            }
            return null;
        }

        @Override
        public Iterable<? extends DependencyGraphNode> getTargets() {
            return targetNodes;
        }

        @Override
        public Set<ComponentArtifactMetadata> getArtifacts(ConfigurationMetadata metaData1) {
            return dependencyMetadata.getArtifacts(from.metaData, metaData1);
        }
    }

    /**
     * Global resolution state.
     */
    private static class ResolveState {
        private final Spec<? super DependencyMetadata> edgeFilter;
        private final Map<ModuleIdentifier, ModuleResolveState> modules = new LinkedHashMap<ModuleIdentifier, ModuleResolveState>();
        private final Map<ResolvedConfigurationIdentifier, NodeState> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, NodeState>();
        private final Map<ModuleVersionSelector, SelectorState> selectors = new LinkedHashMap<ModuleVersionSelector, SelectorState>();
        private final RootNode root;
        private final IdGenerator<Long> idGenerator;
        private final DependencyToComponentIdResolver idResolver;
        private final ComponentMetaDataResolver metaDataResolver;
        private final Set<NodeState> queued = Sets.newHashSet();
        private final LinkedList<NodeState> queue = new LinkedList<NodeState>();
        private final AttributesSchemaInternal attributesSchema;
        private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
        private final ModuleExclusions moduleExclusions;

        public ResolveState(IdGenerator<Long> idGenerator, ComponentResolveResult rootResult, String rootConfigurationName, DependencyToComponentIdResolver idResolver,
                            ComponentMetaDataResolver metaDataResolver, Spec<? super DependencyMetadata> edgeFilter, AttributesSchemaInternal attributesSchema,
                            ImmutableModuleIdentifierFactory moduleIdentifierFactory, ModuleExclusions moduleExclusions) {
            this.idGenerator = idGenerator;
            this.idResolver = idResolver;
            this.metaDataResolver = metaDataResolver;
            this.edgeFilter = edgeFilter;
            this.attributesSchema = attributesSchema;
            this.moduleIdentifierFactory = moduleIdentifierFactory;
            this.moduleExclusions = moduleExclusions;
            ComponentState rootVersion = getRevision(rootResult.getId());
            rootVersion.setMetaData(rootResult.getMetaData());
            root = new RootNode(idGenerator.generateId(), rootVersion, new ResolvedConfigurationIdentifier(rootVersion.id, rootConfigurationName), this);
            nodes.put(root.id, root);
            root.component.module.select(root.component);
        }

        public Collection<ModuleResolveState> getModules() {
            return modules.values();
        }

        public ModuleResolveState getModule(ModuleIdentifier id) {
            ModuleResolveState module = modules.get(id);
            if (module == null) {
                module = new ModuleResolveState(idGenerator, id, this, metaDataResolver);
                modules.put(id, module);
            }
            return module;
        }

        public ComponentState getRevision(ModuleVersionIdentifier id) {
            return getModule(id.getModule()).getVersion(id);
        }

        public Collection<NodeState> getNodes() {
            return nodes.values();
        }

        public NodeState getNode(ComponentState module, ConfigurationMetadata configurationMetadata) {
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(module.id, configurationMetadata.getName());
            NodeState configuration = nodes.get(id);
            if (configuration == null) {
                configuration = new NodeState(idGenerator.generateId(), id, module, this, configurationMetadata);
                nodes.put(id, configuration);
            }
            return configuration;
        }

        public Collection<SelectorState> getSelectors() {
            return selectors.values();
        }

        public SelectorState getSelector(DependencyMetadata dependencyMetadata) {
            ModuleVersionSelector requested = dependencyMetadata.getRequested();
            SelectorState resolveState = selectors.get(requested);
            if (resolveState == null) {
                resolveState = new SelectorState(idGenerator.generateId(), dependencyMetadata, idResolver, this);
                selectors.put(requested, resolveState);
            }
            return resolveState;
        }

        public NodeState peek() {
            return queue.isEmpty() ? null : queue.getFirst();
        }

        public NodeState pop() {
            NodeState next = queue.removeFirst();
            queued.remove(next);
            return next;
        }

        /**
         * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be larger than it previously was, and the node should be visited.
         */
        public void onMoreSelected(NodeState node) {
            // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
            // possible before attempting to resolve them
            if (queued.add(node)) {
                queue.addLast(node);
            }
        }

        /**
         * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be smaller than it previously was, and the node should be visited.
         */
        public void onFewerSelected(NodeState node) {
            // Add to the front of the queue, to flush out configurations that are no longer required.
            if (queued.add(node)) {
                queue.addFirst(node);
            }
        }

        public AttributesSchemaInternal getAttributesSchema() {
            return attributesSchema;
        }
    }

    enum ModuleState {
        New,
        Selected,
        Conflict,
        Evicted
    }

    /**
     * Resolution state for a given module.
     */
    private static class ModuleResolveState implements CandidateModule {
        final ComponentMetaDataResolver metaDataResolver;
        final IdGenerator<Long> idGenerator;
        final ModuleIdentifier id;
        final Set<EdgeState> unattachedDependencies = new LinkedHashSet<EdgeState>();
        final Map<ModuleVersionIdentifier, ComponentState> versions = new LinkedHashMap<ModuleVersionIdentifier, ComponentState>();
        final Set<SelectorState> selectors = new HashSet<SelectorState>();
        final ResolveState resolveState;
        ComponentState selected;

        private ModuleResolveState(IdGenerator<Long> idGenerator, ModuleIdentifier id, ResolveState resolveState, ComponentMetaDataResolver metaDataResolver) {
            this.idGenerator = idGenerator;
            this.id = id;
            this.resolveState = resolveState;
            this.metaDataResolver = metaDataResolver;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        @Override
        public ModuleIdentifier getId() {
            return id;
        }

        @Override
        public Collection<ComponentState> getVersions() {
            return versions.values();
        }

        public ComponentState getSelected() {
            return selected;
        }

        public void select(ComponentState selected) {
            assert this.selected == null;
            this.selected = selected;
            for (ComponentState version : versions.values()) {
                version.state = ModuleState.Evicted;
            }
            selected.state = ModuleState.Selected;
        }

        public ComponentState clearSelection() {
            ComponentState previousSelection = selected;
            selected = null;
            for (ComponentState version : versions.values()) {
                version.state = ModuleState.Conflict;
            }
            return previousSelection;
        }

        public void restart(ComponentState selected) {
            select(selected);
            for (ComponentState version : versions.values()) {
                version.restart(selected);
            }
            for (SelectorState selector : selectors) {
                selector.restart(selected);
            }
            for (EdgeState dependency : new ArrayList<EdgeState>(unattachedDependencies)) {
                dependency.restart(selected);
            }
            unattachedDependencies.clear();
        }

        public void addUnattachedDependency(EdgeState edge) {
            unattachedDependencies.add(edge);
        }

        public void removeUnattachedDependency(EdgeState edge) {
            unattachedDependencies.remove(edge);
        }

        public ComponentState getVersion(ModuleVersionIdentifier id) {
            ComponentState moduleRevision = versions.get(id);
            if (moduleRevision == null) {
                moduleRevision = new ComponentState(idGenerator.generateId(), this, id, metaDataResolver);
                versions.put(id, moduleRevision);
            }
            return moduleRevision;
        }

        public void addSelector(SelectorState selector) {
            selectors.add(selector);
        }
    }

    /**
     * Resolution state for a given component
     */
    public static class ComponentState implements ComponentResolutionState, ComponentResult, DependencyGraphComponent {
        public final ModuleVersionIdentifier id;
        private final ComponentMetaDataResolver resolver;
        private final Set<NodeState> nodes = new LinkedHashSet<NodeState>();
        private final Long resultId;
        private final ModuleResolveState module;
        private volatile ComponentResolveMetadata metaData;
        private ModuleState state = ModuleState.New;
        private ComponentSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;
        private ModuleVersionResolveException failure;
        private SelectorState firstReference;
        private VisitState visitState = VisitState.NotSeen;

        private ComponentState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentMetaDataResolver resolver) {
            this.resultId = resultId;
            this.module = module;
            this.id = id;
            this.resolver = resolver;
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
        public ModuleVersionIdentifier getModuleVersion() {
            return id;
        }

        public ModuleVersionResolveException getFailure() {
            return failure;
        }

        public VisitState getVisitState() {
            return visitState;
        }

        public void setVisitState(VisitState visitState) {
            this.visitState = visitState;
        }

        public Set<NodeState> getNodes() {
            return nodes;
        }

        @Override
        public ComponentResolveMetadata getMetadata() {
            return metaData;
        }

        public void restart(ComponentState selected) {
            for (NodeState configuration : nodes) {
                configuration.restart(selected);
            }
        }

        public void addResolver(SelectorState resolver) {
            if (firstReference == null) {
                firstReference = resolver;
            }
        }

        /**
         * Returns true if this module version can be resolved quickly (already resolved or local)
         *
         * @return true if it has been resolved in a cheap way
         */
        public boolean fastResolve() {
            if (metaData != null || failure != null) {
                return true;
            }

            ComponentIdResolveResult idResolveResult = firstReference.idResolveResult;
            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
                return true;
            }
            if (idResolveResult.getMetaData() != null) {
                metaData = idResolveResult.getMetaData();
                return true;
            }
            return false;
        }

        public void resolve() {
            if (fastResolve()) {
                return;
            }

            ComponentIdResolveResult idResolveResult = firstReference.idResolveResult;

            DefaultBuildableComponentResolveResult result = new DefaultBuildableComponentResolveResult();
            resolver.resolve(idResolveResult.getId(), DefaultComponentOverrideMetadata.forDependency(firstReference.dependencyMetadata), result);
            if (result.getFailure() != null) {
                failure = result.getFailure();
                return;
            }
            metaData = result.getMetaData();
        }

        @Override
        public ComponentResolveMetadata getMetaData() {
            if (metaData == null) {
                resolve();
            }
            return metaData;
        }

        public void setMetaData(ComponentResolveMetadata metaData) {
            this.metaData = metaData;
            this.failure = null;
        }

        public void addConfiguration(NodeState node) {
            nodes.add(node);
        }

        @Override
        public ComponentSelectionReason getSelectionReason() {
            return selectionReason;
        }

        @Override
        public void setSelectionReason(ComponentSelectionReason reason) {
            this.selectionReason = reason;
        }

        @Override
        public ComponentIdentifier getComponentId() {
            return getMetaData().getComponentId();
        }

        @Override
        public Set<ComponentState> getDependents() {
            Set<ComponentState> incoming = new LinkedHashSet<ComponentState>();
            for (NodeState configuration : nodes) {
                for (EdgeState dependencyEdge : configuration.incomingEdges) {
                    incoming.add(dependencyEdge.from.component);
                }
            }
            return incoming;
        }
    }

    enum VisitState {
        NotSeen, Visting, Visited
    }

    /**
     * Represents a node in the dependency graph.
     */
    static class NodeState implements DependencyGraphNode {
        private final Long resultId;
        public final ComponentState component;
        public final Set<EdgeState> incomingEdges = new LinkedHashSet<EdgeState>();
        public final Set<EdgeState> outgoingEdges = new LinkedHashSet<EdgeState>();
        public final ResolvedConfigurationIdentifier id;

        private final ConfigurationMetadata metaData;
        private final ResolveState resolveState;
        private ModuleExclusion previousTraversalExclusions;

        private NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState) {
            this(resultId, id, component, resolveState, component.metaData.getConfiguration(id.getConfiguration()));
        }

        private NodeState(Long resultId, ResolvedConfigurationIdentifier id, ComponentState component, ResolveState resolveState, ConfigurationMetadata md) {
            this.resultId = resultId;
            this.id = id;
            this.component = component;
            this.resolveState = resolveState;
            this.metaData = md;
            component.addConfiguration(this);
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
        public Set<EdgeState> getIncomingEdges() {
            return incomingEdges;
        }

        @Override
        public Set<EdgeState> getOutgoingEdges() {
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
            return metaData.isTransitive();
        }

        public void visitOutgoingDependencies(Collection<EdgeState> target) {
            // If this configuration's version is in conflict, don't do anything
            // If not traversed before, add all selected outgoing edges
            // If traversed before, and the selected modules have changed, remove previous outgoing edges and add outgoing edges again with
            //    the new selections.
            // If traversed before, and the selected modules have not changed, ignore
            // If none of the incoming edges are transitive, then the node has no outgoing edges

            if (component.state != ModuleState.Selected) {
                LOGGER.debug("version for {} is not selected. ignoring.", this);
                return;
            }

            boolean hasIncomingEdges = !incomingEdges.isEmpty();
            List<EdgeState> transitiveIncoming = hasIncomingEdges ? new ArrayList<EdgeState>() : Collections.<EdgeState>emptyList();
            for (EdgeState edge : incomingEdges) {
                if (edge.isTransitive()) {
                    transitiveIncoming.add(edge);
                }
            }

            if (transitiveIncoming.isEmpty() && this != resolveState.root) {
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

            ModuleExclusion resolutionFilter = getModuleResolutionFilter(transitiveIncoming);
            if (previousTraversalExclusions != null) {
                if (previousTraversalExclusions.excludesSameModulesAs(resolutionFilter)) {
                    LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                    // Don't need to traverse again, but hang on to the new filter as the set of artifacts may have changed
                    previousTraversalExclusions = resolutionFilter;
                    return;
                }
                removeOutgoingEdges();
            }

            for (DependencyMetadata dependency : metaData.getDependencies()) {
                if (isExcluded(resolutionFilter, dependency)) {
                    continue;
                }
                EdgeState dependencyEdge = new EdgeState(this, dependency, resolutionFilter, resolveState);
                outgoingEdges.add(dependencyEdge);
                target.add(dependencyEdge);
            }
            previousTraversalExclusions = resolutionFilter;
        }

        private boolean isExcluded(ModuleExclusion selector, DependencyMetadata dependency) {
            if (!resolveState.edgeFilter.isSatisfiedBy(dependency)) {
                LOGGER.debug("{} is filtered.", dependency);
                return true;
            }
            ModuleIdentifier targetModuleId = resolveState.moduleIdentifierFactory.module(dependency.getRequested().getGroup(), dependency.getRequested().getName());
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

        private ModuleExclusion getModuleResolutionFilter(List<EdgeState> transitiveEdges) {
            ModuleExclusion resolutionFilter;
            ModuleExclusions moduleExclusions = resolveState.moduleExclusions;
            if (transitiveEdges.isEmpty()) {
                resolutionFilter = ModuleExclusions.excludeNone();
            } else {
                resolutionFilter = transitiveEdges.get(0).getExclusions(moduleExclusions);
                for (int i = 1; i < transitiveEdges.size(); i++) {
                    EdgeState dependencyEdge = transitiveEdges.get(i);
                    resolutionFilter = moduleExclusions.union(resolutionFilter, dependencyEdge.getExclusions(moduleExclusions));
                }
            }
            resolutionFilter = moduleExclusions.intersect(resolutionFilter, metaData.getExclusions(moduleExclusions));
            return resolutionFilter;
        }

        public void removeOutgoingEdges() {
            for (EdgeState outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
            }
            outgoingEdges.clear();
            previousTraversalExclusions = null;
        }

        public void restart(ComponentState selected) {
            // Restarting this configuration after conflict resolution.
            // If this configuration belongs to the select version, queue ourselves up for traversal.
            // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
            if (component == selected) {
                resolveState.onMoreSelected(this);
            } else {
                for (EdgeState dependency : new ArrayList<EdgeState>(incomingEdges)) {
                    dependency.restart(selected);
                }
                incomingEdges.clear();
            }
        }

        public void deselect() {
            removeOutgoingEdges();
        }
    }

    private static class RootNode extends NodeState {
        private RootNode(Long resultId, ComponentState moduleRevision, ResolvedConfigurationIdentifier id, ResolveState resolveState) {
            super(resultId, id, moduleRevision, resolveState);
        }

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public Set<? extends LocalFileDependencyMetadata> getOutgoingFileEdges() {
            return ((LocalConfigurationMetadata) getMetadata()).getFiles();
        }

        @Override
        public boolean isSelected() {
            return true;
        }

        @Override
        public void deselect() {
        }
    }

    /**
     * Resolution state for a given module version selector.
     */
    private static class SelectorState implements DependencyGraphSelector {
        final Long id;
        final DependencyMetadata dependencyMetadata;
        final DependencyToComponentIdResolver resolver;
        final ResolveState resolveState;
        ModuleVersionResolveException failure;
        ModuleResolveState targetModule;
        ComponentState selected;
        BuildableComponentIdResolveResult idResolveResult;

        private SelectorState(Long id, DependencyMetadata dependencyMetadata, DependencyToComponentIdResolver resolver, ResolveState resolveState) {
            this.id = id;
            this.dependencyMetadata = dependencyMetadata;
            this.resolver = resolver;
            this.resolveState = resolveState;
            targetModule = resolveState.getModule(resolveState.moduleIdentifierFactory.module(dependencyMetadata.getRequested().getGroup(), dependencyMetadata.getRequested().getName()));
        }

        @Override
        public Long getResultId() {
            return id;
        }

        @Override
        public String toString() {
            return dependencyMetadata.toString();
        }

        @Override
        public ComponentSelector getRequested() {
            return dependencyMetadata.getSelector();
        }

        private ModuleVersionResolveException getFailure() {
            return failure != null ? failure : selected.getFailure();
        }

        public ComponentSelectionReason getSelectionReason() {
            return selected == null ? idResolveResult.getSelectionReason() : selected.getSelectionReason();
        }

        public ComponentState getSelected() {
            return targetModule.selected;
        }

        public ModuleResolveState getSelectedModule() {
            return targetModule;
        }

        /**
         * @return The module version, or null if there is a failure to resolve this selector.
         */
        public ComponentState resolveModuleRevisionId() {
            if (selected != null) {
                return selected;
            }
            if (failure != null) {
                return null;
            }

            idResolveResult = new DefaultBuildableComponentIdResolveResult();
            resolver.resolve(dependencyMetadata, idResolveResult);
            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
                return null;
            }

            selected = resolveState.getRevision(idResolveResult.getModuleVersionId());
            selected.addResolver(this);
            selected.selectionReason = idResolveResult.getSelectionReason();
            targetModule = selected.module;
            targetModule.addSelector(this);

            return selected;
        }

        public void restart(ComponentState moduleRevision) {
            this.selected = moduleRevision;
            this.targetModule = moduleRevision.module;
        }
    }

    private static class DirectDependencyForcingResolver implements ModuleConflictResolver {
        private final ComponentState root;

        private DirectDependencyForcingResolver(ComponentState root) {
            this.root = root;
        }

        public <T extends ComponentResolutionState> T select(Collection<? extends T> candidates) {
            for (NodeState configuration : root.nodes) {
                for (EdgeState outgoingEdge : configuration.outgoingEdges) {
                    if (outgoingEdge.dependencyMetadata.isForce() && candidates.contains(outgoingEdge.targetModuleRevision)) {
                        outgoingEdge.targetModuleRevision.selectionReason = VersionSelectionReasons.FORCED;
                        return (T) outgoingEdge.targetModuleRevision;
                    }
                }
            }
            return null;
        }
    }

    private static class DownloadMetadataOperation implements RunnableBuildOperation {
        private final ComponentState state;

        DownloadMetadataOperation(ComponentState state) {
            this.state = state;
        }

        @Override
        public void run(BuildOperationContext context) {
            state.getMetaData();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Resolve " + state);
        }
    }
}
