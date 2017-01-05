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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
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
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
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
    private final AttributesSchema attributesSchema;

    public DependencyGraphBuilder(DependencyToComponentIdResolver componentIdResolver, ComponentMetaDataResolver componentMetaDataResolver,
                                  ResolveContextToComponentResolver resolveContextToComponentResolver,
                                  ConflictHandler conflictHandler, Spec<? super DependencyMetadata> edgeFilter, AttributesSchema attributesSchema) {
        this.idResolver = componentIdResolver;
        this.metaDataResolver = componentMetaDataResolver;
        this.moduleResolver = resolveContextToComponentResolver;
        this.conflictHandler = conflictHandler;
        this.edgeFilter = edgeFilter;
        this.attributesSchema = attributesSchema;
    }

    public void resolve(ResolveContext resolveContext, DependencyGraphVisitor modelVisitor) {
        IdGenerator<Long> idGenerator = new LongIdGenerator();
        DefaultBuildableComponentResolveResult rootModule = new DefaultBuildableComponentResolveResult();
        moduleResolver.resolve(resolveContext, rootModule);

        ResolveState resolveState = new ResolveState(idGenerator, rootModule, resolveContext.getName(), idResolver, metaDataResolver, edgeFilter, attributesSchema);
        conflictHandler.registerResolver(new DirectDependencyForcingResolver(resolveState.root.moduleRevision));

        traverseGraph(resolveState, conflictHandler);
        resolveState.root.moduleRevision.setSelectionReason(VersionSelectionReasons.ROOT);

        assembleResult(resolveState, modelVisitor);
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(final ResolveState resolveState, final ConflictHandler conflictHandler) {
        resolveState.onMoreSelected(resolveState.root);

        List<DependencyEdge> dependencies = new ArrayList<DependencyEdge>();
        while (resolveState.peek() != null || conflictHandler.hasConflicts()) {
            if (resolveState.peek() != null) {
                ConfigurationNode node = resolveState.pop();
                LOGGER.debug("Visiting configuration {}.", node);

                // Calculate the outgoing edges of this configuration
                dependencies.clear();
                node.visitOutgoingDependencies(dependencies);

                for (DependencyEdge dependency : dependencies) {
                    LOGGER.debug("Visiting dependency {}", dependency);

                    // Resolve dependency to a particular revision
                    ModuleVersionResolveState moduleRevision = dependency.resolveModuleRevisionId();
                    if (moduleRevision == null) {
                        // Failed to resolve.
                        continue;
                    }
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
                                    ModuleVersionResolveState previouslySelected = resolveState.getModule(module).clearSelection();
                                    if (previouslySelected != null) {
                                        for (ConfigurationNode configuration : previouslySelected.configurations) {
                                            configuration.deselect();
                                        }
                                    }
                                }
                            });
                        }
                    }

                    dependency.attachToTargetConfigurations();
                }
            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                conflictHandler.resolveNextConflict(new Action<ConflictResolutionResult>() {
                    public void execute(final ConflictResolutionResult result) {
                        result.getConflict().withParticipatingModules(new Action<ModuleIdentifier>() {
                            public void execute(ModuleIdentifier moduleIdentifier) {
                                ModuleVersionResolveState selected = result.getSelected();
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

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, DependencyGraphVisitor visitor) {
        visitor.start(resolveState.root);

        // Visit the nodes
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                visitor.visitNode(resolvedConfiguration);
            }
        }

        // Visit the selectors
        for (DependencyGraphSelector selector : resolveState.getSelectors()) {
            visitor.visitSelector(selector);
        }

        // Visit the edges
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                visitor.visitEdges(resolvedConfiguration);
            }
        }

        visitor.finish(resolveState.root);  
    }

    /**
     * Represents the edges in the dependency graph.
     */
    private static class DependencyEdge implements DependencyGraphEdge {
        public final ConfigurationNode from;
        public final ModuleVersionSelectorResolveState selector;

        private final DependencyMetadata dependencyMetadata;
        private final ResolveState resolveState;
        private final ModuleExclusion moduleExclusion;
        private final Set<ConfigurationNode> targetConfigurations = new LinkedHashSet<ConfigurationNode>();
        private ModuleVersionResolveState targetModuleRevision;

        DependencyEdge(ConfigurationNode from, DependencyMetadata dependencyMetadata, ModuleExclusion moduleExclusion, ResolveState resolveState) {
            this.from = from;
            this.dependencyMetadata = dependencyMetadata;
            this.moduleExclusion = moduleExclusion;
            this.resolveState = resolveState;
            selector = resolveState.getSelector(dependencyMetadata);
        }

        @Override
        public String toString() {
            return String.format("%s -> %s", from.toString(), dependencyMetadata);
        }

        @Override
        public DependencyGraphNode getFrom() {
            return from;
        }

        @Override
        public DependencyGraphSelector getSelector() {
            return selector;
        }

        /**
         * @return The resolved module version
         */
        public ModuleVersionResolveState resolveModuleRevisionId() {
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
            for (ConfigurationNode targetConfiguration : targetConfigurations) {
                targetConfiguration.addIncomingEdge(this);
            }
            if (!targetConfigurations.isEmpty()) {
                selector.getSelectedModule().removeUnattachedDependency(this);
            }
        }

        public void removeFromTargetConfigurations() {
            for (ConfigurationNode targetConfiguration : targetConfigurations) {
                targetConfiguration.removeIncomingEdge(this);
            }
            targetConfigurations.clear();
            if (targetModuleRevision != null) {
                selector.getSelectedModule().removeUnattachedDependency(this);
            }
        }

        public void restart(ModuleVersionResolveState selected) {
            removeFromTargetConfigurations();
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetConfigurations.clear();
            ComponentResolveMetadata targetModuleVersion = targetModuleRevision.getMetaData();
            if (targetModuleVersion == null) {
                // Broken version
                return;
            }

            Set<ConfigurationMetadata> targetConfigurations = dependencyMetadata.selectConfigurations(from.moduleRevision.metaData, from.metaData, targetModuleVersion, resolveState.getAttributesSchema());
            for (ConfigurationMetadata targetConfiguration : targetConfigurations) {
                ConfigurationNode targetConfigurationNode = resolveState.getConfigurationNode(targetModuleRevision, targetConfiguration);
                this.targetConfigurations.add(targetConfigurationNode);
            }
        }

        @Override
        public ModuleExclusion getExclusions() {
            ModuleExclusion edgeExclusions = dependencyMetadata.getExclusions(from.metaData);
            return ModuleExclusions.intersect(edgeExclusions, moduleExclusion);
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
            return targetConfigurations;
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
        private final Map<ResolvedConfigurationIdentifier, ConfigurationNode> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationNode>();
        private final Map<ModuleVersionSelector, ModuleVersionSelectorResolveState> selectors = new LinkedHashMap<ModuleVersionSelector, ModuleVersionSelectorResolveState>();
        private final RootConfigurationNode root;
        private final IdGenerator<Long> idGenerator;
        private final DependencyToComponentIdResolver idResolver;
        private final ComponentMetaDataResolver metaDataResolver;
        private final Set<ConfigurationNode> queued = new HashSet<ConfigurationNode>();
        private final LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();
        private final AttributesSchema attributesSchema;

        public ResolveState(IdGenerator<Long> idGenerator, ComponentResolveResult rootResult, String rootConfigurationName, DependencyToComponentIdResolver idResolver,
                            ComponentMetaDataResolver metaDataResolver, Spec<? super DependencyMetadata> edgeFilter, AttributesSchema attributesSchema) {
            this.idGenerator = idGenerator;
            this.idResolver = idResolver;
            this.metaDataResolver = metaDataResolver;
            this.edgeFilter = edgeFilter;
            this.attributesSchema = attributesSchema;
            ModuleVersionResolveState rootVersion = getRevision(rootResult.getId());
            rootVersion.setMetaData(rootResult.getMetaData());
            root = new RootConfigurationNode(idGenerator.generateId(), rootVersion, new ResolvedConfigurationIdentifier(rootVersion.id, rootConfigurationName), this);
            nodes.put(root.id, root);
            root.moduleRevision.module.select(root.moduleRevision);
        }

        public ModuleResolveState getModule(ModuleIdentifier id) {
            ModuleResolveState module = modules.get(id);
            if (module == null) {
                module = new ModuleResolveState(idGenerator, id, this, metaDataResolver);
                modules.put(id, module);
            }
            return module;
        }

        public ModuleVersionResolveState getRevision(ModuleVersionIdentifier id) {
            return getModule(id.getModule()).getVersion(id);
        }

        public Collection<ConfigurationNode> getConfigurationNodes() {
            return nodes.values();
        }

        public ConfigurationNode getConfigurationNode(ModuleVersionResolveState module, ConfigurationMetadata configurationMetadata) {
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(module.id, configurationMetadata.getName());
            ConfigurationNode configuration = nodes.get(id);
            if (configuration == null) {
                configuration = new ConfigurationNode(idGenerator.generateId(), id, module, this, configurationMetadata);
                nodes.put(id, configuration);
            }
            return configuration;
        }

        public Collection<ModuleVersionSelectorResolveState> getSelectors() {
            return selectors.values();
        }

        public ModuleVersionSelectorResolveState getSelector(DependencyMetadata dependencyMetadata) {
            ModuleVersionSelector requested = dependencyMetadata.getRequested();
            ModuleVersionSelectorResolveState resolveState = selectors.get(requested);
            if (resolveState == null) {
                resolveState = new ModuleVersionSelectorResolveState(idGenerator.generateId(), dependencyMetadata, idResolver, this);
                selectors.put(requested, resolveState);
            }
            return resolveState;
        }

        public ConfigurationNode peek() {
            return queue.isEmpty() ? null : queue.getFirst();
        }

        public ConfigurationNode pop() {
            ConfigurationNode next = queue.removeFirst();
            queued.remove(next);
            return next;
        }

        /**
         * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be larger than it previously was, and the node should be visited.
         */
        public void onMoreSelected(ConfigurationNode configuration) {
            // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
            // possible before attempting to resolve them
            if (queued.add(configuration)) {
                queue.addLast(configuration);
            }
        }

        /**
         * Called when a change is made to a configuration node, such that its dependency graph <em>may</em> now be smaller than it previously was, and the node should be visited.
         */
        public void onFewerSelected(ConfigurationNode configuration) {
            // Add to the front of the queue, to flush out configurations that are no longer required.
            if (queued.add(configuration)) {
                queue.addFirst(configuration);
            }
        }

        public AttributesSchema getAttributesSchema() {
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
        final Set<DependencyEdge> unattachedDependencies = new LinkedHashSet<DependencyEdge>();
        final Map<ModuleVersionIdentifier, ModuleVersionResolveState> versions = new LinkedHashMap<ModuleVersionIdentifier, ModuleVersionResolveState>();
        final Set<ModuleVersionSelectorResolveState> selectors = new HashSet<ModuleVersionSelectorResolveState>();
        final ResolveState resolveState;
        ModuleVersionResolveState selected;

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
        public Collection<ModuleVersionResolveState> getVersions() {
            return versions.values();
        }

        public void select(ModuleVersionResolveState selected) {
            assert this.selected == null;
            this.selected = selected;
            for (ModuleVersionResolveState version : versions.values()) {
                version.state = ModuleState.Evicted;
            }
            selected.state = ModuleState.Selected;
        }

        public ModuleVersionResolveState clearSelection() {
            ModuleVersionResolveState previousSelection = selected;
            selected = null;
            for (ModuleVersionResolveState version : versions.values()) {
                version.state = ModuleState.Conflict;
            }
            return previousSelection;
        }

        public void restart(ModuleVersionResolveState selected) {
            select(selected);
            for (ModuleVersionResolveState version : versions.values()) {
                version.restart(selected);
            }
            for (ModuleVersionSelectorResolveState selector : selectors) {
                selector.restart(selected);
            }
            for (DependencyEdge dependency : new ArrayList<DependencyEdge>(unattachedDependencies)) {
                dependency.restart(selected);
            }
            unattachedDependencies.clear();
        }

        public void addUnattachedDependency(DependencyEdge edge) {
            unattachedDependencies.add(edge);
        }

        public void removeUnattachedDependency(DependencyEdge edge) {
            unattachedDependencies.remove(edge);
        }

        public ModuleVersionResolveState getVersion(ModuleVersionIdentifier id) {
            ModuleVersionResolveState moduleRevision = versions.get(id);
            if (moduleRevision == null) {
                moduleRevision = new ModuleVersionResolveState(idGenerator.generateId(), this, id, metaDataResolver);
                versions.put(id, moduleRevision);
            }

            return moduleRevision;
        }

        public void addSelector(ModuleVersionSelectorResolveState selector) {
            selectors.add(selector);
        }
    }

    /**
     * Resolution state for a given module version.
     */
    public static class ModuleVersionResolveState implements ComponentResolutionState, ComponentResult, DependencyGraphComponent {
        public final ModuleVersionIdentifier id;
        private final ComponentMetaDataResolver resolver;
        private final Set<ConfigurationNode> configurations = new LinkedHashSet<ConfigurationNode>();
        private final Long resultId;
        private final ModuleResolveState module;
        private ComponentResolveMetadata metaData;
        private ModuleState state = ModuleState.New;
        private ComponentSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;
        private ModuleVersionResolveException failure;
        private ModuleVersionSelectorResolveState firstReference;

        private ModuleVersionResolveState(Long resultId, ModuleResolveState module, ModuleVersionIdentifier id, ComponentMetaDataResolver resolver) {
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

        @Override
        public ComponentResolveMetadata getMetadata() {
            return metaData;
        }

        public void restart(ModuleVersionResolveState selected) {
            for (ConfigurationNode configuration : configurations) {
                configuration.restart(selected);
            }
        }

        public void addResolver(ModuleVersionSelectorResolveState resolver) {
            if (firstReference == null) {
                firstReference = resolver;
            }
        }

        public void resolve() {
            if (metaData != null || failure != null) {
                return;
            }

            ComponentIdResolveResult idResolveResult = firstReference.idResolveResult;
            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
                return;
            }
            if (idResolveResult.getMetaData() != null) {
                metaData = idResolveResult.getMetaData();
                return;
            }

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

        public void addConfiguration(ConfigurationNode configurationNode) {
            configurations.add(configurationNode);
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
        public Set<ModuleVersionResolveState> getDependents() {
            Set<ModuleVersionResolveState> incoming = new LinkedHashSet<ModuleVersionResolveState>();
            for (DependencyGraphBuilder.ConfigurationNode configuration : configurations) {
                for (DependencyGraphBuilder.DependencyEdge dependencyEdge : configuration.incomingEdges) {
                    incoming.add(dependencyEdge.from.moduleRevision);
                }
            }
            return incoming;
        }
    }

    /**
     * Represents a node in the dependency graph.
     */
    static class ConfigurationNode implements DependencyGraphNode {
        private final Long resultId;
        public final ModuleVersionResolveState moduleRevision;
        public final Set<DependencyEdge> incomingEdges = new LinkedHashSet<DependencyEdge>();
        public final Set<DependencyEdge> outgoingEdges = new LinkedHashSet<DependencyEdge>();
        public final ResolvedConfigurationIdentifier id;

        private final ConfigurationMetadata metaData;
        private final ResolveState resolveState;
        private ModuleExclusion previousTraversalExclusions;

        private ConfigurationNode(Long resultId, ResolvedConfigurationIdentifier id, ModuleVersionResolveState moduleRevision, ResolveState resolveState) {
            this(resultId, id, moduleRevision, resolveState, moduleRevision.metaData.getConfiguration(id.getConfiguration()));
        }

        private ConfigurationNode(Long resultId, ResolvedConfigurationIdentifier id, ModuleVersionResolveState moduleRevision, ResolveState resolveState, ConfigurationMetadata md) {
            this.resultId = resultId;
            this.id = id;
            this.moduleRevision = moduleRevision;
            this.resolveState = resolveState;
            this.metaData = md;
            moduleRevision.addConfiguration(this);
        }

        @Override
        public Long getNodeId() {
            return resultId;
        }

        @Override
        public ResolvedConfigurationIdentifier getResolvedConfigurationId() {
            return id;
        }

        @Override
        public DependencyGraphComponent getOwner() {
            return moduleRevision;
        }

        @Override
        public Set<DependencyEdge> getIncomingEdges() {
            return incomingEdges;
        }

        @Override
        public Set<DependencyEdge> getOutgoingEdges() {
            return outgoingEdges;
        }

        @Override
        public ConfigurationMetadata getMetadata() {
            return metaData;
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, id.getConfiguration());
        }

        public boolean isTransitive() {
            return metaData.isTransitive();
        }

        public void visitOutgoingDependencies(Collection<DependencyEdge> target) {
            // If this configuration's version is in conflict, don't do anything
            // If not traversed before, add all selected outgoing edges
            // If traversed before, and the selected modules have changed, remove previous outgoing edges and add outgoing edges again with
            //    the new selections.
            // If traversed before, and the selected modules have not changed, ignore
            // If none of the incoming edges are transitive, then the node has no outgoing edges

            if (moduleRevision.state != ModuleState.Selected) {
                LOGGER.debug("version for {} is not selected. ignoring.", this);
                return;
            }

            List<DependencyEdge> transitiveIncoming = new ArrayList<DependencyEdge>();
            for (DependencyEdge edge : incomingEdges) {
                if (edge.isTransitive()) {
                    transitiveIncoming.add(edge);
                }
            }

            if (transitiveIncoming.isEmpty() && this != resolveState.root) {
                if (previousTraversalExclusions != null) {
                    removeOutgoingEdges();
                }
                if (incomingEdges.isEmpty()) {
                    LOGGER.debug("{} has no incoming edges. ignoring.", this);
                } else {
                    LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
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
                DependencyEdge dependencyEdge = new DependencyEdge(this, dependency, resolutionFilter, resolveState);
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
            ModuleIdentifier targetModuleId = DefaultModuleIdentifier.newId(dependency.getRequested().getGroup(), dependency.getRequested().getName());
            if (selector.excludeModule(targetModuleId)) {
                LOGGER.debug("{} is excluded from {}.", targetModuleId, this);
                return true;
            }

            return false;
        }

        public void addIncomingEdge(DependencyEdge dependencyEdge) {
            incomingEdges.add(dependencyEdge);
            resolveState.onMoreSelected(this);
        }

        public void removeIncomingEdge(DependencyEdge dependencyEdge) {
            incomingEdges.remove(dependencyEdge);
            resolveState.onFewerSelected(this);
        }

        public boolean isSelected() {
            return !incomingEdges.isEmpty();
        }

        private ModuleExclusion getModuleResolutionFilter(List<DependencyEdge> transitiveEdges) {
            ModuleExclusion resolutionFilter;
            if (transitiveEdges.isEmpty()) {
                resolutionFilter = ModuleExclusions.excludeNone();
            } else {
                resolutionFilter = transitiveEdges.get(0).getExclusions();
                for (int i = 1; i < transitiveEdges.size(); i++) {
                    DependencyEdge dependencyEdge = transitiveEdges.get(i);
                    resolutionFilter = ModuleExclusions.union(resolutionFilter, dependencyEdge.getExclusions());
                }
            }
            resolutionFilter = ModuleExclusions.intersect(resolutionFilter, metaData.getExclusions());
            return resolutionFilter;
        }

        public void removeOutgoingEdges() {
            for (DependencyEdge outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
            }
            outgoingEdges.clear();
            previousTraversalExclusions = null;
        }

        public void restart(ModuleVersionResolveState selected) {
            // Restarting this configuration after conflict resolution.
            // If this configuration belongs to the select version, queue ourselves up for traversal.
            // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
            if (moduleRevision == selected) {
                resolveState.onMoreSelected(this);
            } else {
                for (DependencyEdge dependency : new ArrayList<DependencyEdge>(incomingEdges)) {
                    dependency.restart(selected);
                }
                incomingEdges.clear();
            }
        }

        public void deselect() {
            removeOutgoingEdges();
        }
    }

    private static class RootConfigurationNode extends ConfigurationNode {
        private RootConfigurationNode(Long resultId, ModuleVersionResolveState moduleRevision, ResolvedConfigurationIdentifier id, ResolveState resolveState) {
            super(resultId, id, moduleRevision, resolveState);
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
    private static class ModuleVersionSelectorResolveState implements DependencyGraphSelector {
        final Long id;
        final DependencyMetadata dependencyMetadata;
        final DependencyToComponentIdResolver resolver;
        final ResolveState resolveState;
        ModuleVersionResolveException failure;
        ModuleResolveState targetModule;
        ModuleVersionResolveState targetModuleRevision;
        BuildableComponentIdResolveResult idResolveResult;

        private ModuleVersionSelectorResolveState(Long id, DependencyMetadata dependencyMetadata, DependencyToComponentIdResolver resolver, ResolveState resolveState) {
            this.id = id;
            this.dependencyMetadata = dependencyMetadata;
            this.resolver = resolver;
            this.resolveState = resolveState;
            targetModule = resolveState.getModule(new DefaultModuleIdentifier(dependencyMetadata.getRequested().getGroup(), dependencyMetadata.getRequested().getName()));
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
            return failure != null ? failure : targetModuleRevision.getFailure();
        }

        public ComponentSelectionReason getSelectionReason() {
            return targetModuleRevision == null ? idResolveResult.getSelectionReason() : targetModuleRevision.getSelectionReason();
        }

        public ModuleVersionResolveState getSelected() {
            return targetModule.selected;
        }

        public ModuleResolveState getSelectedModule() {
            return targetModule;
        }

        /**
         * @return The module version, or null if there is a failure to resolve this selector.
         */
        public ModuleVersionResolveState resolveModuleRevisionId() {
            if (targetModuleRevision != null) {
                return targetModuleRevision;
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

            targetModuleRevision = resolveState.getRevision(idResolveResult.getModuleVersionId());
            targetModuleRevision.addResolver(this);
            targetModuleRevision.selectionReason = idResolveResult.getSelectionReason();
            targetModule = targetModuleRevision.module;
            targetModule.addSelector(this);

            return targetModuleRevision;
        }

        public void restart(ModuleVersionResolveState moduleRevision) {
            this.targetModuleRevision = moduleRevision;
            this.targetModule = moduleRevision.module;
        }
    }

    private static class DirectDependencyForcingResolver implements ModuleConflictResolver {
        private final ModuleVersionResolveState root;

        private DirectDependencyForcingResolver(ModuleVersionResolveState root) {
            this.root = root;
        }

        public <T extends ComponentResolutionState> T select(Collection<? extends T> candidates) {
            for (ConfigurationNode configuration : root.configurations) {
                for (DependencyEdge outgoingEdge : configuration.outgoingEdges) {
                    if (outgoingEdge.dependencyMetadata.isForce() && candidates.contains(outgoingEdge.targetModuleRevision)) {
                        outgoingEdge.targetModuleRevision.selectionReason = VersionSelectionReasons.FORCED;
                        return (T) outgoingEdge.targetModuleRevision;
                    }
                }
            }
            return null;
        }
    }
}
