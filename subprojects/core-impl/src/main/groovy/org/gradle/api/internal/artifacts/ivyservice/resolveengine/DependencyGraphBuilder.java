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

import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfigurationMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleVersionMetaData;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.InternalDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ModuleVersionSelection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedConfigurationListener;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final DependencyToModuleVersionIdResolver dependencyResolver;
    private final CacheLockingManager cacheLockingManager;
    private final InternalConflictResolver conflictResolver;
    private final ModuleToModuleVersionResolver moduleResolver;

    public DependencyGraphBuilder(ResolvedArtifactFactory resolvedArtifactFactory,
                                  DependencyToModuleVersionIdResolver dependencyResolver,
                                  ModuleToModuleVersionResolver moduleResolver,
                                  ModuleConflictResolver conflictResolver,
                                  CacheLockingManager cacheLockingManager) {
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.dependencyResolver = dependencyResolver;
        this.moduleResolver = moduleResolver;
        this.cacheLockingManager = cacheLockingManager;
        this.conflictResolver = new InternalConflictResolver(conflictResolver);
    }

    public DefaultLenientConfiguration resolve(ConfigurationInternal configuration, ResolveData resolveData, ResolvedConfigurationListener listener) throws ResolveException {
        DefaultBuildableModuleVersionResolveResult rootModule = new DefaultBuildableModuleVersionResolveResult();
        moduleResolver.resolve(configuration.getModule(), configuration.getAll(), rootModule);

        ResolveState resolveState = new ResolveState(rootModule, configuration.getName(), dependencyResolver, resolveData);
        traverseGraph(resolveState);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.root.getResult(), cacheLockingManager);
        assembleResult(resolveState, result, listener);

        return result;
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
                    DefaultModuleRevisionResolveState moduleRevision = dependency.resolveModuleRevisionId();
                    if (moduleRevision == null) {
                        // Failed to resolve.
                        continue;
                    }
                    ModuleIdentifier moduleId = moduleRevision.id.getModule();

                    // Check for a new conflict
                    if (moduleRevision.state == ModuleState.New) {
                        ModuleResolveState module = resolveState.getModule(moduleId);

                        // A new module revision. Check for conflict
                        Collection<DefaultModuleRevisionResolveState> versions = module.getVersions();
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
                            DefaultModuleRevisionResolveState previouslySelected = module.clearSelection();
                            if (previouslySelected != null) {
                                for (ConfigurationNode configuration : previouslySelected.configurations) {
                                    configuration.removeOutgoingEdges();
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
                DefaultModuleRevisionResolveState selected = conflictResolver.select(module.getVersions(), resolveState.root.moduleRevision);
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
    private void assembleResult(ResolveState resolveState, ResolvedConfigurationBuilder result, ResolvedConfigurationListener listener) {
        FailureState failureState = new FailureState(resolveState.root);
        ModuleVersionIdentifier root = resolveState.root.toId();
        listener.start(root);

        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                resolvedConfiguration.attachToParents(resolvedArtifactFactory, result);
                resolvedConfiguration.collectFailures(failureState);
                listener.resolvedModuleVersion(resolvedConfiguration.moduleRevision);
            }
        }
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            if (resolvedConfiguration.isSelected()) {
                listener.resolvedConfiguration(resolvedConfiguration.toId(), resolvedConfiguration.outgoingEdges);
            }
        }
        failureState.attachFailures(result);
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
            
            Map<DefaultModuleRevisionResolveState, List<ModuleVersionIdentifier>> shortestPaths = new LinkedHashMap<DefaultModuleRevisionResolveState, List<ModuleVersionIdentifier>>();
            List<ModuleVersionIdentifier> rootPath = new ArrayList<ModuleVersionIdentifier>();
            rootPath.add(root.moduleRevision.id);
            shortestPaths.put(root.moduleRevision, rootPath);

            Set<DefaultModuleRevisionResolveState> directDependees = new LinkedHashSet<DefaultModuleRevisionResolveState>();
            for (ConfigurationNode node : brokenDependency.requiredBy) {
                directDependees.add(node.moduleRevision);
            }

            Set<DefaultModuleRevisionResolveState> seen = new HashSet<DefaultModuleRevisionResolveState>();
            LinkedList<DefaultModuleRevisionResolveState> queue = new LinkedList<DefaultModuleRevisionResolveState>();
            queue.addAll(directDependees);
            while (!queue.isEmpty()) {
                DefaultModuleRevisionResolveState version = queue.getFirst();
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
            for (DefaultModuleRevisionResolveState version : directDependees) {
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

    private static class DependencyEdge implements InternalDependencyResult {
        private final ConfigurationNode from;
        private final DependencyDescriptor dependencyDescriptor;
        private final DependencyMetaData dependencyMetaData;
        private final Set<String> targetConfigurationRules;
        private final ResolveState resolveState;
        private final ModuleVersionSpec selectorSpec;
        private final Set<ConfigurationNode> targetConfigurations = new LinkedHashSet<ConfigurationNode>();
        private final ModuleVersionSelectorResolveState selector;
        private DefaultModuleRevisionResolveState targetModuleRevision;

        public DependencyEdge(ConfigurationNode from, DependencyMetaData dependencyMetaData, Set<String> targetConfigurationRules, ModuleVersionSpec selectorSpec, ResolveState resolveState) {
            this.from = from;
            this.dependencyMetaData = dependencyMetaData;
            this.dependencyDescriptor = dependencyMetaData.getDescriptor();
            this.targetConfigurationRules = targetConfigurationRules;
            this.selectorSpec = selectorSpec;
            this.resolveState = resolveState;
            selector = resolveState.getSelector(dependencyMetaData);
        }

        @Override
        public String toString() {
            return String.format("%s -> %s(%s)", from.toString(), dependencyMetaData.getRequested(), targetConfigurationRules);
        }

        /**
         * @return The resolved module version
         */
        public DefaultModuleRevisionResolveState resolveModuleRevisionId() {
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
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            selector.restart(selected);
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetConfigurations.clear();
            ModuleVersionMetaData targetModuleVersion = targetModuleRevision.getMetaData();
            if (targetModuleVersion == null) {
                // Broken version
                return;
            }

            ModuleDescriptor targetDescriptor = targetModuleVersion.getDescriptor();
            IvyNode node = new IvyNode(resolveState.resolveData, targetDescriptor);
            Set<String> targets = new LinkedHashSet<String>();
            for (String targetConfiguration : targetConfigurationRules) {
                Collections.addAll(targets, node.getRealConfs(targetConfiguration));
            }

            for (String targetConfigurationName : targets) {
                // TODO - this is the wrong spot for this check
                if (targetDescriptor.getConfiguration(targetConfigurationName) == null) {
                    throw new RuntimeException(String.format("Module version %s, configuration:%s declares a dependency on configuration '%s' which is not declared in the module descriptor for %s",
                            from.moduleRevision.id, from.configurationName,
                            targetConfigurationName, targetModuleRevision.id));
                }
                ConfigurationNode targetConfiguration = resolveState.getConfigurationNode(targetModuleRevision, targetConfigurationName);
                targetConfigurations.add(targetConfiguration);
            }
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationNode childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory) {
            String[] targetConfigurations = from.metaData.getHierarchy().toArray(new String[from.metaData.getHierarchy().size()]);
            DependencyArtifactDescriptor[] dependencyArtifacts = dependencyDescriptor.getDependencyArtifacts(targetConfigurations);
            if (dependencyArtifacts.length == 0) {
                return Collections.emptySet();
            }
            Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
            for (DependencyArtifactDescriptor artifactDescriptor : dependencyArtifacts) {
                MDArtifact artifact = new MDArtifact(childConfiguration.descriptor, artifactDescriptor.getName(), artifactDescriptor.getType(), artifactDescriptor.getExt(), artifactDescriptor.getUrl(), artifactDescriptor.getQualifiedExtraAttributes());
                artifacts.add(resolvedArtifactFactory.create(childConfiguration.getResult(), artifact, targetModuleRevision.resolve().getArtifactResolver()));
            }
            return artifacts;
        }

        public void attachToParents(ConfigurationNode childConfiguration, ResolvedArtifactFactory artifactFactory, ResolvedConfigurationBuilder result) {
            DefaultResolvedDependency parent = from.getResult();
            DefaultResolvedDependency child = childConfiguration.getResult();
            parent.addChild(child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration, artifactFactory);
            if (artifacts.isEmpty()) {
                artifacts = childConfiguration.getArtifacts(artifactFactory);
            }
            child.addParentSpecificArtifacts(parent, artifacts);

            for (ResolvedArtifact artifact : artifacts) {
                result.addArtifact(artifact);
            }

            if (parent == result.getRoot()) {
                EnhancedDependencyDescriptor enhancedDependencyDescriptor = (EnhancedDependencyDescriptor) dependencyDescriptor;
                result.addFirstLevelDependency(enhancedDependencyDescriptor.getModuleDependency(), child);
            }
        }

        public ModuleVersionSpec getSelector() {
            String[] configurations = from.metaData.getHierarchy().toArray(new String[from.metaData.getHierarchy().size()]);
            ModuleVersionSpec selector = ModuleVersionSpec.forExcludes(dependencyDescriptor.getExcludeRules(configurations));
            return selector.intersect(selectorSpec);
        }

        public ModuleVersionSelector getRequested() {
            return dependencyMetaData.getRequested();
        }

        public ModuleVersionResolveException getFailure() {
            return selector.getFailure();
        }

        public DefaultModuleRevisionResolveState getSelected() {
            return selector.getSelected();
        }

        public ModuleVersionSelectionReason getReason() {
            return selector.getSelectionReason();
        }

        public void collectFailures(FailureState failureState) {
            ModuleVersionResolveException failure = getFailure();
            if (failure != null) {
                failureState.addUnresolvedDependency(this, selector.dependencyMetaData.getRequested(), failure);
            }
        }
    }

    private static class ResolveState {
        private final Map<ModuleIdentifier, ModuleResolveState> modules = new LinkedHashMap<ModuleIdentifier, ModuleResolveState>();
        private final Map<ResolvedConfigurationIdentifier, ConfigurationNode> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationNode>();
        private final Map<ModuleVersionSelector, ModuleVersionSelectorResolveState> selectors = new LinkedHashMap<ModuleVersionSelector, ModuleVersionSelectorResolveState>();
        private final ConfigurationNode root;
        private final DependencyToModuleVersionIdResolver resolver;
        private final ResolveData resolveData;
        private final Set<ConfigurationNode> queued = new HashSet<ConfigurationNode>();
        private final LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();

        public ResolveState(ModuleVersionResolveResult rootResult, String rootConfigurationName, DependencyToModuleVersionIdResolver resolver, ResolveData resolveData) {
            this.resolver = resolver;
            this.resolveData = resolveData;
            DefaultModuleRevisionResolveState rootVersion = getRevision(rootResult.getId());
            rootVersion.setResolveResult(rootResult);
            root = getConfigurationNode(rootVersion, rootConfigurationName);
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

        public DefaultModuleRevisionResolveState getRevision(ModuleVersionIdentifier id) {
            return getModule(id.getModule()).getVersion(id);
        }

        public Collection<ConfigurationNode> getConfigurationNodes() {
            return nodes.values();
        }

        public ConfigurationNode getConfigurationNode(DefaultModuleRevisionResolveState module, String configurationName) {
            ModuleVersionIdentifier original = module.id;
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(original.getGroup(), original.getName(), original.getVersion(), configurationName);
            ConfigurationNode configuration = nodes.get(id);
            if (configuration == null) {
                configuration = new ConfigurationNode(module, module.metaData, configurationName, this);
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

    private static class ModuleResolveState {
        final ModuleIdentifier id;
        final Set<DependencyEdge> unattachedDependencies = new LinkedHashSet<DependencyEdge>();
        final Map<ModuleVersionIdentifier, DefaultModuleRevisionResolveState> versions = new LinkedHashMap<ModuleVersionIdentifier, DefaultModuleRevisionResolveState>();
        final ResolveState resolveState;
        DefaultModuleRevisionResolveState selected;

        private ModuleResolveState(ModuleIdentifier id, ResolveState resolveState) {
            this.id = id;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public Collection<DefaultModuleRevisionResolveState> getVersions() {
            return versions.values();
        }

        public void select(DefaultModuleRevisionResolveState selected) {
            assert this.selected == null;
            this.selected = selected;
            for (DefaultModuleRevisionResolveState version : versions.values()) {
                version.state = ModuleState.Evicted;
            }
            selected.state = ModuleState.Selected;
        }

        public DefaultModuleRevisionResolveState clearSelection() {
            DefaultModuleRevisionResolveState previousSelection = selected;
            selected = null;
            for (DefaultModuleRevisionResolveState version : versions.values()) {
                version.state = ModuleState.Conflict;
            }
            return previousSelection;
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            select(selected);
            for (DefaultModuleRevisionResolveState version : versions.values()) {
                version.restart(selected);
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

        public DefaultModuleRevisionResolveState getVersion(ModuleVersionIdentifier id) {
            DefaultModuleRevisionResolveState moduleRevision = versions.get(id);
            if (moduleRevision == null) {
                moduleRevision = new DefaultModuleRevisionResolveState(this, id, resolveState);
                versions.put(id, moduleRevision);
            }

            return moduleRevision;
        }
    }

    private static class DefaultModuleRevisionResolveState implements ModuleRevisionResolveState, ModuleVersionSelection {
        final ModuleResolveState module;
        final ModuleVersionIdentifier id;
        final ResolveState resolveState;
        final Set<ConfigurationNode> configurations = new LinkedHashSet<ConfigurationNode>();
        List<DependencyMetaData> dependencies;
        ModuleVersionMetaData metaData;
        ModuleState state = ModuleState.New;
        ModuleVersionSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;
        ModuleVersionIdResolveResult idResolveResult;
        ModuleVersionResolveResult resolveResult;
        ModuleVersionResolveException failure;

        private DefaultModuleRevisionResolveState(ModuleResolveState module, ModuleVersionIdentifier id, ResolveState resolveState) {
            this.module = module;
            this.id = id;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public String getRevision() {
            return id.getVersion();
        }

        public Iterable<DependencyMetaData> getDependencies() {
            if (dependencies == null) {
                dependencies = getMetaData().getDependencies();
            }
            return dependencies;
        }

        public String getId() {
            return String.format("%s:%s:%s", id.getGroup(), id.getName(), id.getVersion());
        }

        public ModuleVersionResolveException getFailure() {
            return failure;
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            for (ConfigurationNode configuration : configurations) {
                configuration.restart(selected);
            }
        }

        public void addResolver(ModuleVersionSelectorResolveState resolver) {
            if (this.idResolveResult == null) {
                idResolveResult = resolver.idResolveResult;
            }
        }

        public ModuleVersionResolveResult resolve() {
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

        public ModuleVersionMetaData getMetaData() {
            if (metaData == null) {
                resolve();
            }
            return metaData;
        }

        public void setResolveResult(ModuleVersionResolveResult resolveResult) {
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

        public ModuleVersionSelectionReason getSelectionReason() {
            return selectionReason;
        }

        public void setSelectionReason(ModuleVersionSelectionReason reason) {
            this.selectionReason = reason;
        }
    }

    private static class ConfigurationNode {
        final DefaultModuleRevisionResolveState moduleRevision;
        final ModuleVersionMetaData moduleMetaData;
        final ConfigurationMetaData metaData;
        final ResolveState resolveState;
        final DefaultModuleDescriptor descriptor;
        final String configurationName;
        final Set<DependencyEdge> incomingEdges = new LinkedHashSet<DependencyEdge>();
        final Set<DependencyEdge> outgoingEdges = new LinkedHashSet<DependencyEdge>();
        DefaultResolvedDependency result;
        ModuleVersionSpec previousTraversal;
        Set<ResolvedArtifact> artifacts;

        private ConfigurationNode(DefaultModuleRevisionResolveState moduleRevision, ModuleVersionMetaData moduleMetaData, String configurationName, ResolveState resolveState) {
            this.moduleRevision = moduleRevision;
            this.moduleMetaData = moduleMetaData;
            this.resolveState = resolveState;
            this.descriptor = (DefaultModuleDescriptor) moduleMetaData.getDescriptor();
            this.configurationName = configurationName;
            this.metaData = moduleMetaData.getConfiguration(configurationName);
            moduleRevision.addConfiguration(this);
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, configurationName);
        }

        public Set<ResolvedArtifact> getArtifacts(ResolvedArtifactFactory resolvedArtifactFactory) {
            if (artifacts == null) {
                artifacts = new LinkedHashSet<ResolvedArtifact>();
                for (Artifact artifact : metaData.getArtifacts()) {
                    artifacts.add(resolvedArtifactFactory.create(getResult(), artifact, moduleRevision.resolve().getArtifactResolver()));
                }
            }
            return artifacts;
        }

        public DefaultResolvedDependency getResult() {
            if (result == null) {
                result = new DefaultResolvedDependency(
                        moduleRevision.id.getGroup(),
                        moduleRevision.id.getName(),
                        moduleRevision.id.getVersion(),
                        configurationName);
            }

            return result;
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

            for (DependencyMetaData dependency : moduleRevision.getDependencies()) {
                DependencyDescriptor dependencyDescriptor = dependency.getDescriptor();
                ModuleId targetModuleId = dependencyDescriptor.getDependencyRevisionId().getModuleId();
                Set<String> targetConfigurations = getTargetConfigurations(dependencyDescriptor);
                if (!targetConfigurations.isEmpty()) {
                    if (!selectorSpec.isSatisfiedBy(targetModuleId)) {
                        LOGGER.debug("{} is excluded from {}.", targetModuleId, this);
                    } else {
                        DependencyEdge dependencyEdge = new DependencyEdge(this, dependency, targetConfigurations, selectorSpec, resolveState);
                        outgoingEdges.add(dependencyEdge);
                        target.add(dependencyEdge);
                    }
                }
            }
            previousTraversal = selectorSpec;
        }

        Set<String> getTargetConfigurations(DependencyDescriptor dependencyDescriptor) {
            Set<String> targetConfigurations = new LinkedHashSet<String>();
            for (String moduleConfiguration : dependencyDescriptor.getModuleConfigurations()) {
                if (moduleConfiguration.equals("*") || metaData.getHierarchy().contains(moduleConfiguration)) {
                    for (String targetConfiguration : dependencyDescriptor.getDependencyConfigurations(moduleConfiguration)) {
                        targetConfigurations.add(targetConfiguration);
                    }
                }
            }
            return targetConfigurations;
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

        public void attachToParents(ResolvedArtifactFactory artifactFactory, ResolvedConfigurationBuilder result) {
            LOGGER.debug("Attaching {} to its parents.", this);
            for (DependencyEdge dependency : incomingEdges) {
                dependency.attachToParents(this, artifactFactory, result);
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
            String[] configurations = metaData.getHierarchy().toArray(new String[metaData.getHierarchy().size()]);
            selector = selector.intersect(ModuleVersionSpec.forExcludes(descriptor.getExcludeRules(configurations)));
            return selector;
        }

        public void removeOutgoingEdges() {
            for (DependencyEdge outgoingDependency : outgoingEdges) {
                outgoingDependency.removeFromTargetConfigurations();
            }
            outgoingEdges.clear();
            previousTraversal = null;
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            // Restarting this configuration after conflict resolution.
            // If this configuration belongs to the select version, queue ourselves up for traversal.
            // If not, then move our incoming edges across to the selected configuration
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
    }

    private static class ModuleVersionSelectorResolveState {
        final DependencyToModuleVersionIdResolver resolver;
        final ResolveState resolveState;
        final DependencyMetaData dependencyMetaData;
        ModuleVersionResolveException failure;
        ModuleResolveState targetModule;
        DefaultModuleRevisionResolveState targetModuleRevision;
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

        public ModuleVersionSelectionReason getSelectionReason() {
            return targetModuleRevision == null ? idResolveResult.getSelectionReason() : targetModuleRevision.getSelectionReason();
        }

        public DefaultModuleRevisionResolveState getSelected() {
            return targetModule.selected;
        }

        public ModuleResolveState getSelectedModule() {
            return targetModule;
        }

        /**
         * @return The module version, or null if there is a failure to resolve this selector.
         */
        public DefaultModuleRevisionResolveState resolveModuleRevisionId() {
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

            return targetModuleRevision;
        }

        public void restart(DefaultModuleRevisionResolveState moduleRevision) {
            this.targetModuleRevision = moduleRevision;
            this.targetModule = moduleRevision.module;
        }
    }

    private static class InternalConflictResolver {
        private final ModuleConflictResolver resolver;

        private InternalConflictResolver(ModuleConflictResolver resolver) {
            this.resolver = resolver;
        }

        DefaultModuleRevisionResolveState select(Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState root) {
            for (ConfigurationNode configuration : root.configurations) {
                for (DependencyEdge outgoingEdge : configuration.outgoingEdges) {
                    if (outgoingEdge.dependencyDescriptor.isForce() && candidates.contains(outgoingEdge.targetModuleRevision)) {
                        outgoingEdge.targetModuleRevision.selectionReason = VersionSelectionReasons.FORCED;
                        return outgoingEdge.targetModuleRevision;
                    }
                }
            }
            return (DefaultModuleRevisionResolveState) resolver.select(candidates);
        }
    }
}