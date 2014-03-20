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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleId;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult.ResolvedConfigurationBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.InternalDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ModuleVersionSelection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.api.internal.artifacts.metadata.ComponentArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.ConfigurationMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final DependencyToModuleVersionIdResolver dependencyResolver;
    private final DependencyToConfigurationResolver dependencyToConfigurationResolver;
    private final InternalConflictResolver conflictResolver;
    private final ModuleToModuleVersionResolver moduleResolver;
    private final ArtifactResolver artifactResolver;

    public DependencyGraphBuilder(DependencyToModuleVersionIdResolver dependencyResolver,
                                  ModuleToModuleVersionResolver moduleResolver,
                                  ArtifactResolver artifactResolver,
                                  ModuleConflictResolver conflictResolver,
                                  DependencyToConfigurationResolver dependencyToConfigurationResolver) {
        this.dependencyResolver = dependencyResolver;
        this.moduleResolver = moduleResolver;
        this.artifactResolver = artifactResolver;
        this.dependencyToConfigurationResolver = dependencyToConfigurationResolver;
        this.conflictResolver = new InternalConflictResolver(conflictResolver);
    }

    public void resolve(ConfigurationInternal configuration,
                        ResolutionResultBuilder newModelBuilder,
                        ResolvedConfigurationBuilder oldModelBuilder) throws ResolveException {
        DefaultBuildableComponentResolveResult rootModule = new DefaultBuildableComponentResolveResult();
        moduleResolver.resolve(configuration.getModule(), configuration.getAll(), rootModule);

        ResolveState resolveState = new ResolveState(rootModule, configuration.getName(), dependencyResolver, dependencyToConfigurationResolver, artifactResolver, oldModelBuilder);
        traverseGraph(resolveState);

        assembleResult(resolveState, oldModelBuilder, newModelBuilder);
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(ResolveState resolveState) {
        Set<ModuleIdentifier> conflicts = new LinkedHashSet<ModuleIdentifier>();

        resolveState.onMoreSelected(resolveState.root);

        List<DependencyEdge> dependencies = new ArrayList<DependencyEdge>();
        while (resolveState.peek() != null || !conflicts.isEmpty()) {
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
                        Collection<ModuleVersionResolveState> versions = module.getVersions();
                        if (versions.size() == 1) {
                            // First version of this module. Select it for now
                            LOGGER.debug("Selecting new module version {}", moduleRevision);
                            module.select(moduleRevision);
                        } else {
                            // Not the first version of this module. We have a new conflict
                            LOGGER.debug("Found new conflicting module version {}", moduleRevision);
                            conflicts.add(moduleId);

                            // Deselect the currently selected version, and remove all outgoing edges from the version
                            // This will propagate through the graph and prune configurations that are no longer required
                            ModuleVersionResolveState previouslySelected = module.clearSelection();
                            if (previouslySelected != null) {
                                for (ConfigurationNode configuration : previouslySelected.configurations) {
                                    configuration.deselect();
                                }
                            }
                        }
                    }

                    dependency.attachToTargetConfigurations();
                }
            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                ModuleIdentifier moduleId = conflicts.iterator().next();
                conflicts.remove(moduleId);
                ModuleResolveState module = resolveState.getModule(moduleId);
                ModuleVersionResolveState selected = conflictResolver.select(module.getVersions(), resolveState.root.moduleRevision);
                LOGGER.debug("Selected {} from conflicting modules {}.", selected, module.getVersions());

                // Restart each configuration. For the evicted configuration, this means moving incoming dependencies across to the
                // matching selected configuration. For the select configuration, this mean traversing its dependencies.
                module.restart(selected);
            }
        }
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, ResolvedConfigurationBuilder oldModelBuilder, ResolutionResultBuilder newModelBuilder) {
        FailureState failureState = new FailureState(resolveState.root);
        newModelBuilder.start(resolveState.root.moduleRevision.id, resolveState.root.metaData.getComponent().getComponentId());

        // Visit the nodes
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                resolvedConfiguration.validate();
                oldModelBuilder.newResolvedDependency(resolvedConfiguration.id);
                resolvedConfiguration.collectFailures(failureState);
                newModelBuilder.resolvedModuleVersion(resolvedConfiguration.moduleRevision);
            }
        }
        // Visit the edges
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                resolvedConfiguration.attachToParents(oldModelBuilder);
                newModelBuilder.resolvedConfiguration(resolvedConfiguration.toId(), resolvedConfiguration.outgoingEdges);
            }
        }
        failureState.attachFailures(oldModelBuilder);
        oldModelBuilder.done(resolveState.root.id);
    }

    private static class FailureState {
        final Map<ModuleVersionSelector, BrokenDependency> failuresByRevisionId = new LinkedHashMap<ModuleVersionSelector, BrokenDependency>();
        final ConfigurationNode root;

        private FailureState(ConfigurationNode root) {
            this.root = root;
        }

        public void attachFailures(ResolvedConfigurationBuilder result) {
            for (Map.Entry<ModuleVersionSelector, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
                Collection<List<ModuleVersionIdentifier>> paths = calculatePaths(entry.getValue());
                result.addUnresolvedDependency(new DefaultUnresolvedDependency(entry.getKey(), entry.getValue().failure.withIncomingPaths(paths)));
            }
        }

        private Collection<List<ModuleVersionIdentifier>> calculatePaths(BrokenDependency brokenDependency) {
            // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root
            
            Map<ModuleVersionResolveState, List<ModuleVersionIdentifier>> shortestPaths = new LinkedHashMap<ModuleVersionResolveState, List<ModuleVersionIdentifier>>();
            List<ModuleVersionIdentifier> rootPath = new ArrayList<ModuleVersionIdentifier>();
            rootPath.add(root.moduleRevision.id);
            shortestPaths.put(root.moduleRevision, rootPath);

            Set<ModuleVersionResolveState> directDependees = new LinkedHashSet<ModuleVersionResolveState>();
            for (ConfigurationNode node : brokenDependency.requiredBy) {
                directDependees.add(node.moduleRevision);
            }

            Set<ModuleVersionResolveState> seen = new HashSet<ModuleVersionResolveState>();
            LinkedList<ModuleVersionResolveState> queue = new LinkedList<ModuleVersionResolveState>();
            queue.addAll(directDependees);
            while (!queue.isEmpty()) {
                ModuleVersionResolveState version = queue.getFirst();
                if (version == root.moduleRevision) {
                    queue.removeFirst();
                } else if (seen.add(version)) {
                    for (ConfigurationNode configuration : version.configurations) {
                        for (DependencyEdge dependencyEdge : configuration.incomingEdges) {
                            queue.add(0, dependencyEdge.from.moduleRevision);
                        }
                    }
                } else {
                    queue.remove();
                    List<ModuleVersionIdentifier> shortest = null;
                    for (ConfigurationNode configuration : version.configurations) {
                        for (DependencyEdge dependencyEdge : configuration.incomingEdges) {
                            List<ModuleVersionIdentifier> candidate = shortestPaths.get(dependencyEdge.from.moduleRevision);
                            if (candidate == null) {
                                continue;
                            }
                            if (shortest == null) {
                                shortest = candidate;
                            } else if (shortest.size() > candidate.size()) {
                                shortest = candidate;
                            }
                        }
                    }
                    if (shortest == null) {
                        continue;
                    }
                    List<ModuleVersionIdentifier> path = new ArrayList<ModuleVersionIdentifier>();
                    path.addAll(shortest);
                    path.add(version.id);
                    shortestPaths.put(version, path);
                }
            }

            List<List<ModuleVersionIdentifier>> paths = new ArrayList<List<ModuleVersionIdentifier>>();
            for (ModuleVersionResolveState version : directDependees) {
                List<ModuleVersionIdentifier> path = shortestPaths.get(version);
                paths.add(path);
            }
            return paths;
        }

        public void addUnresolvedDependency(DependencyEdge dependency, ModuleVersionSelector requested, ModuleVersionResolveException failure) {
            BrokenDependency breakage = failuresByRevisionId.get(requested);
            if (breakage == null) {
                breakage = new BrokenDependency(failure);
                failuresByRevisionId.put(requested, breakage);
            }
            breakage.requiredBy.add(dependency.from);
        }
        
        private static class BrokenDependency {
            final ModuleVersionResolveException failure;
            final List<ConfigurationNode> requiredBy = new ArrayList<ConfigurationNode>();

            private BrokenDependency(ModuleVersionResolveException failure) {
                this.failure = failure;
            }
        }
    }

    /**
     * Represents the edges in the dependency graph.
     */
    private static class DependencyEdge implements InternalDependencyResult {
        private final ConfigurationNode from;
        private final DependencyDescriptor dependencyDescriptor;
        private final DependencyMetaData dependencyMetaData;
        private final ResolveState resolveState;
        private final ModuleVersionSpec selectorSpec;
        private final Set<ConfigurationNode> targetConfigurations = new LinkedHashSet<ConfigurationNode>();
        private final ModuleVersionSelectorResolveState selector;
        private ModuleVersionResolveState targetModuleRevision;

        public DependencyEdge(ConfigurationNode from, DependencyMetaData dependencyMetaData, ModuleVersionSpec selectorSpec, ResolveState resolveState) {
            this.from = from;
            this.dependencyMetaData = dependencyMetaData;
            this.dependencyDescriptor = dependencyMetaData.getDescriptor();
            this.selectorSpec = selectorSpec;
            this.resolveState = resolveState;
            selector = resolveState.getSelector(dependencyMetaData);
        }

        @Override
        public String toString() {
            return String.format("%s -> %s(%s)", from.toString(), dependencyMetaData.getRequested(), dependencyDescriptor);
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
            return from.isTransitive() && dependencyMetaData.isTransitive();
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
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetConfigurations.clear();
            ComponentMetaData targetModuleVersion = targetModuleRevision.getMetaData();
            if (targetModuleVersion == null) {
                // Broken version
                return;
            }

            Set<ConfigurationMetaData> targetConfigurations = resolveState.dependencyToConfigurationResolver.resolveTargetConfigurations(dependencyMetaData, from.metaData, targetModuleVersion);
            for (ConfigurationMetaData targetConfiguration : targetConfigurations) {
                ConfigurationNode targetConfigurationNode = resolveState.getConfigurationNode(targetModuleRevision, targetConfiguration.getName());
                this.targetConfigurations.add(targetConfigurationNode);
            }
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationNode childConfiguration) {
            Set<ComponentArtifactMetaData> dependencyArtifacts = dependencyMetaData.getArtifacts(from.metaData, childConfiguration.metaData);
            if (dependencyArtifacts.isEmpty()) {
                return Collections.emptySet();
            }
            Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
            for (ComponentArtifactMetaData artifact : dependencyArtifacts) {
                artifacts.add(resolveState.builder.newArtifact(childConfiguration.id, childConfiguration.metaData.getComponent(), artifact, resolveState.artifactResolver));
            }
            return artifacts;
        }

        public void attachToParents(ConfigurationNode childConfiguration, ResolvedConfigurationBuilder oldModelBuilder) {
            ResolvedConfigurationIdentifier parent = from.id;
            ResolvedConfigurationIdentifier child = childConfiguration.id;
            oldModelBuilder.addChild(parent, child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration);
            if (artifacts.isEmpty()) {
                artifacts = childConfiguration.getArtifacts();
            }
            //TODO SF merge with addChild
            oldModelBuilder.addParentSpecificArtifacts(child, parent, artifacts);

            if (parent == resolveState.root.id) {
                EnhancedDependencyDescriptor enhancedDependencyDescriptor = (EnhancedDependencyDescriptor) dependencyDescriptor;
                oldModelBuilder.addFirstLevelDependency(enhancedDependencyDescriptor.getModuleDependency(), child);
            }
        }

        public ModuleVersionSpec getSelector() {
            String[] configurations = from.metaData.getHierarchy().toArray(new String[from.metaData.getHierarchy().size()]);
            ModuleVersionSpec selector = ModuleVersionSpec.forExcludes(dependencyDescriptor.getExcludeRules(configurations));
            return selector.intersect(selectorSpec);
        }

        public ComponentSelector getRequested() {
            return dependencyMetaData.getSelector();
        }

        public ModuleVersionResolveException getFailure() {
            return selector.getFailure();
        }

        public ModuleVersionIdentifier getSelected() {
            return selector.getSelected().getSelectedId();
        }

        public ComponentSelectionReason getReason() {
            return selector.getSelectionReason();
        }

        public void collectFailures(FailureState failureState) {
            ModuleVersionResolveException failure = getFailure();
            if (failure != null) {
                failureState.addUnresolvedDependency(this, selector.dependencyMetaData.getRequested(), failure);
            }
        }
    }

    /**
     * Global resolution state.
     */
    private static class ResolveState {
        private final Map<ModuleIdentifier, ModuleResolveState> modules = new LinkedHashMap<ModuleIdentifier, ModuleResolveState>();
        private final Map<ResolvedConfigurationIdentifier, ConfigurationNode> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationNode>();
        private final Map<ModuleVersionSelector, ModuleVersionSelectorResolveState> selectors = new LinkedHashMap<ModuleVersionSelector, ModuleVersionSelectorResolveState>();
        private final RootConfigurationNode root;
        private final DependencyToModuleVersionIdResolver resolver;
        private final DependencyToConfigurationResolver dependencyToConfigurationResolver;
        private final ArtifactResolver artifactResolver;
        private final ResolvedConfigurationBuilder builder;
        private final Set<ConfigurationNode> queued = new HashSet<ConfigurationNode>();
        private final LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();

        public ResolveState(ComponentResolveResult rootResult, String rootConfigurationName, DependencyToModuleVersionIdResolver resolver,
                            DependencyToConfigurationResolver dependencyToConfigurationResolver, ArtifactResolver artifactResolver, ResolvedConfigurationBuilder builder) {
            this.resolver = resolver;
            this.dependencyToConfigurationResolver = dependencyToConfigurationResolver;
            this.artifactResolver = artifactResolver;
            this.builder = builder;
            ModuleVersionResolveState rootVersion = getRevision(rootResult.getId());
            rootVersion.setResolveResult(rootResult);
            root = new RootConfigurationNode(rootVersion, new ResolvedConfigurationIdentifier(rootVersion.id, rootConfigurationName), this);
            nodes.put(root.id, root);
            root.moduleRevision.module.select(root.moduleRevision);
        }

        public ModuleResolveState getModule(ModuleIdentifier id) {
            ModuleResolveState module = modules.get(id);
            if (module == null) {
                module = new ModuleResolveState(id, this);
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

        public ConfigurationNode getConfigurationNode(ModuleVersionResolveState module, String configurationName) {
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(module.id, configurationName);
            ConfigurationNode configuration = nodes.get(id);
            if (configuration == null) {
                configuration = new ConfigurationNode(module, id, this);
                nodes.put(id, configuration);
            }
            return configuration;
        }

        public ModuleVersionSelectorResolveState getSelector(DependencyMetaData dependencyMetaData) {
            ModuleVersionSelector requested = dependencyMetaData.getRequested();
            ModuleVersionSelectorResolveState resolveState = selectors.get(requested);
            if (resolveState == null) {
                resolveState = new ModuleVersionSelectorResolveState(dependencyMetaData, resolver, this);
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
    private static class ModuleResolveState {
        final ModuleIdentifier id;
        final Set<DependencyEdge> unattachedDependencies = new LinkedHashSet<DependencyEdge>();
        final Map<ModuleVersionIdentifier, ModuleVersionResolveState> versions = new LinkedHashMap<ModuleVersionIdentifier, ModuleVersionResolveState>();
        final Set<ModuleVersionSelectorResolveState> selectors = new HashSet<ModuleVersionSelectorResolveState>();
        final ResolveState resolveState;
        ModuleVersionResolveState selected;

        private ModuleResolveState(ModuleIdentifier id, ResolveState resolveState) {
            this.id = id;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return id.toString();
        }

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
                moduleRevision = new ModuleVersionResolveState(this, id, resolveState);
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
    private static class ModuleVersionResolveState implements ModuleRevisionResolveState, ModuleVersionSelection {
        final ModuleResolveState module;
        final ModuleVersionIdentifier id;
        final ResolveState resolveState;
        final Set<ConfigurationNode> configurations = new LinkedHashSet<ConfigurationNode>();
        ComponentMetaData metaData;
        ModuleState state = ModuleState.New;
        ComponentSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;
        ModuleVersionIdResolveResult idResolveResult;
        ComponentResolveResult resolveResult;
        ModuleVersionResolveException failure;

        private ModuleVersionResolveState(ModuleResolveState module, ModuleVersionIdentifier id, ResolveState resolveState) {
            this.module = module;
            this.id = id;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public String getVersion() {
            return id.getVersion();
        }

        public String getId() {
            return String.format("%s:%s:%s", id.getGroup(), id.getName(), id.getVersion());
        }

        public ModuleVersionResolveException getFailure() {
            return failure;
        }

        public void restart(ModuleVersionResolveState selected) {
            for (ConfigurationNode configuration : configurations) {
                configuration.restart(selected);
            }
        }

        public void addResolver(ModuleVersionSelectorResolveState resolver) {
            if (this.idResolveResult == null) {
                idResolveResult = resolver.idResolveResult;
            }
        }

        public ComponentResolveResult resolve() {
            if (resolveResult != null) {
                return resolveResult;
            }
            if (failure != null) {
                return null;
            }

            resolveResult = idResolveResult.resolve();
            if (resolveResult.getFailure() != null) {
                failure = resolveResult.getFailure();
                return null;
            }
            setResolveResult(resolveResult);
            return resolveResult;
        }

        public ComponentMetaData getMetaData() {
            if (metaData == null) {
                resolve();
            }
            return metaData;
        }

        public void setResolveResult(ComponentResolveResult resolveResult) {
            this.resolveResult = resolveResult;
            this.metaData = resolveResult.getMetaData();
            this.failure = null;
        }

        public void addConfiguration(ConfigurationNode configurationNode) {
            configurations.add(configurationNode);
        }

        public ModuleVersionIdentifier getSelectedId() {
            return id;
        }

        public ComponentSelectionReason getSelectionReason() {
            return selectionReason;
        }

        public void setSelectionReason(ComponentSelectionReason reason) {
            this.selectionReason = reason;
        }

        public ComponentIdentifier getComponentId() {
            return getMetaData().getComponentId();
        }
    }

    /**
     * Represents a node in the dependency graph.
     */
    private static class ConfigurationNode {
        final ModuleVersionResolveState moduleRevision;
        final ConfigurationMetaData metaData;
        final ResolveState resolveState;
        final Set<DependencyEdge> incomingEdges = new LinkedHashSet<DependencyEdge>();
        final Set<DependencyEdge> outgoingEdges = new LinkedHashSet<DependencyEdge>();
        final ResolvedConfigurationIdentifier id;
        ModuleVersionSpec previousTraversal;
        Set<ResolvedArtifact> artifacts;

        private ConfigurationNode(ModuleVersionResolveState moduleRevision, ResolvedConfigurationIdentifier id, ResolveState resolveState) {
            this.moduleRevision = moduleRevision;
            this.resolveState = resolveState;
            this.metaData = moduleRevision.metaData.getConfiguration(id.getConfiguration());
            this.id = id;
            moduleRevision.addConfiguration(this);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, metaData.getName());
        }

        public Set<ResolvedArtifact> getArtifacts() {
            if (artifacts == null) {
                artifacts = new LinkedHashSet<ResolvedArtifact>();

                BuildableArtifactSetResolveResult result = new DefaultBuildableArtifactSetResolveResult();
                resolveState.artifactResolver.resolveModuleArtifacts(metaData.getComponent(), new ConfigurationResolveContext(metaData.getName()), result);

                for (ComponentArtifactMetaData artifact : result.getArtifacts()) {
                    artifacts.add(resolveState.builder.newArtifact(id, metaData.getComponent(), artifact, resolveState.artifactResolver));
                }
            }
            return artifacts;
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
                if (previousTraversal != null) {
                    removeOutgoingEdges();
                }
                if (incomingEdges.isEmpty()) {
                    LOGGER.debug("{} has no incoming edges. ignoring.", this);
                } else {
                    LOGGER.debug("{} has no transitive incoming edges. ignoring outgoing edges.", this);
                }
                return;
            }

            ModuleVersionSpec selectorSpec = getSelector(transitiveIncoming);
            if (previousTraversal != null) {
                if (previousTraversal.acceptsSameModulesAs(selectorSpec)) {
                    LOGGER.debug("Changed edges for {} selects same versions as previous traversal. ignoring", this);
                    return;
                }
                removeOutgoingEdges();
            }

            for (DependencyMetaData dependency : metaData.getDependencies()) {
                DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
                ModuleId targetModuleId = dependencyDescriptor.getDependencyRevisionId().getModuleId();
                if (!selectorSpec.isSatisfiedBy(targetModuleId)) {
                    LOGGER.debug("{} is excluded from {}.", targetModuleId, this);
                    continue;
                }
                DependencyEdge dependencyEdge = new DependencyEdge(this, dependency, selectorSpec, resolveState);
                outgoingEdges.add(dependencyEdge);
                target.add(dependencyEdge);
            }
            previousTraversal = selectorSpec;
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
            return moduleRevision.state == ModuleState.Selected;
        }

        public void attachToParents(ResolvedConfigurationBuilder oldModelBuilder) {
            LOGGER.debug("Attaching {} to its parents.", this);
            for (DependencyEdge dependency : incomingEdges) {
                dependency.attachToParents(this, oldModelBuilder);
            }
        }

        public void collectFailures(FailureState failureState) {
            for (DependencyEdge dependency : outgoingEdges) {
                dependency.collectFailures(failureState);
            }
        }

        private ModuleVersionSpec getSelector(List<DependencyEdge> transitiveEdges) {
            ModuleVersionSpec selector;
            if (transitiveEdges.isEmpty()) {
                selector = ModuleVersionSpec.forExcludes(); //includes all
            } else {
                selector = transitiveEdges.get(0).getSelector();
                for (int i = 1; i < transitiveEdges.size(); i++) {
                    DependencyEdge dependencyEdge = transitiveEdges.get(i);
                    selector = selector.union(dependencyEdge.getSelector());
                }
            }
            selector = selector.intersect(ModuleVersionSpec.forExcludes(metaData.getExcludeRules()));
            return selector;
        }

        public void removeOutgoingEdges() {
            for (DependencyEdge outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
            }
            outgoingEdges.clear();
            previousTraversal = null;
        }

        public void restart(ModuleVersionResolveState selected) {
            // Restarting this configuration after conflict resolution.
            // If this configuration belongs to the select version, queue ourselves up for traversal.
            // If not, then remove our incoming edges, which triggers them to be moved across to the selected configuration
            if (moduleRevision == selected) {
                resolveState.onMoreSelected(this);
            } else {
                for (DependencyEdge dependency : incomingEdges) {
                    dependency.restart(selected);
                }
                incomingEdges.clear();
            }
        }

        private ModuleVersionIdentifier toId() {
            return moduleRevision.id;
        }

        public void validate() {
            for (DependencyEdge incomingEdge : incomingEdges) {
                ConfigurationNode fromNode = incomingEdge.from;
                if (!fromNode.isSelected()) {
                    throw new IllegalStateException(String.format("Unexpected state %s for parent node for dependency from %s to %s.", fromNode.moduleRevision.state, fromNode, this));
                }
            }
        }

        public void deselect() {
            removeOutgoingEdges();
        }
    }

    private static class RootConfigurationNode extends ConfigurationNode {
        private RootConfigurationNode(ModuleVersionResolveState moduleRevision, ResolvedConfigurationIdentifier id, ResolveState resolveState) {
            super(moduleRevision, id, resolveState);
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
    private static class ModuleVersionSelectorResolveState {
        final DependencyToModuleVersionIdResolver resolver;
        final ResolveState resolveState;
        final DependencyMetaData dependencyMetaData;
        ModuleVersionResolveException failure;
        ModuleResolveState targetModule;
        ModuleVersionResolveState targetModuleRevision;
        ModuleVersionIdResolveResult idResolveResult;

        private ModuleVersionSelectorResolveState(DependencyMetaData dependencyMetaData, DependencyToModuleVersionIdResolver resolver, ResolveState resolveState) {
            this.dependencyMetaData = dependencyMetaData;
            this.resolver = resolver;
            this.resolveState = resolveState;
            targetModule = resolveState.getModule(new DefaultModuleIdentifier(dependencyMetaData.getRequested().getGroup(), dependencyMetaData.getRequested().getName()));
        }

        @Override
        public String toString() {
            return dependencyMetaData.toString();
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

            idResolveResult = resolver.resolve(dependencyMetaData);
            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
                return null;
            }

            targetModuleRevision = resolveState.getRevision(idResolveResult.getId());
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

    private static class InternalConflictResolver {
        private final ModuleConflictResolver resolver;

        private InternalConflictResolver(ModuleConflictResolver resolver) {
            this.resolver = resolver;
        }

        ModuleVersionResolveState select(Collection<ModuleVersionResolveState> candidates, ModuleVersionResolveState root) {
            for (ConfigurationNode configuration : root.configurations) {
                for (DependencyEdge outgoingEdge : configuration.outgoingEdges) {
                    if (outgoingEdge.dependencyDescriptor.isForce() && candidates.contains(outgoingEdge.targetModuleRevision)) {
                        outgoingEdge.targetModuleRevision.selectionReason = VersionSelectionReasons.FORCED;
                        return outgoingEdge.targetModuleRevision;
                    }
                }
            }
            return resolver.select(candidates);
        }
    }
}