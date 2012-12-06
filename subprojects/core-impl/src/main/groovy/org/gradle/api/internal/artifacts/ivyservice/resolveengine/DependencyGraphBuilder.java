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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.result.ModuleVersionSelectionReason;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.InternalDependencyResult;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ModuleVersionSelection;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolvedConfigurationListener;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier.newId;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final DependencyToModuleVersionIdResolver dependencyResolver;
    private final ForcedModuleConflictResolver conflictResolver;

    public DependencyGraphBuilder(ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory, DependencyToModuleVersionIdResolver dependencyResolver, ModuleConflictResolver conflictResolver) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.dependencyResolver = dependencyResolver;
        this.conflictResolver = new ForcedModuleConflictResolver(conflictResolver);
    }

    public DefaultLenientConfiguration resolve(ConfigurationInternal configuration, ResolveData resolveData, ResolvedConfigurationListener listener) throws ResolveException {
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configuration.getAll(), configuration.getModule());

        ResolveState resolveState = new ResolveState(moduleDescriptor, configuration.getName(), dependencyResolver, resolveData);
        traverseGraph(resolveState);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.root.getResult());
        assembleResult(resolveState, result, listener);

        return result;
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(ResolveState resolveState) {
        Set<ModuleId> conflicts = new LinkedHashSet<ModuleId>();

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
                    dependency.resolveModuleRevisionId();
                    DefaultModuleRevisionResolveState moduleRevision = dependency.getTargetModuleRevision();
                    if (moduleRevision == null) {
                        // Failed to resolve.
                        continue;
                    }
                    ModuleId moduleId= moduleRevision.id.getModuleId();

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
                ModuleId moduleId = conflicts.iterator().next();
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
            resolvedConfiguration.attachToParents(resolvedArtifactFactory, result);
            resolvedConfiguration.collectFailures(failureState);

            listener.resolvedConfiguration(resolvedConfiguration.toId(), resolvedConfiguration.outgoingEdges);
        }
        failureState.attachFailures(result);
    }

    private static class FailureState {
        final Map<ModuleRevisionId, BrokenDependency> failuresByRevisionId = new LinkedHashMap<ModuleRevisionId, BrokenDependency>();
        final ConfigurationNode root;

        private FailureState(ConfigurationNode root) {
            this.root = root;
        }

        public void attachFailures(ResolvedConfigurationBuilder result) {
            for (Map.Entry<ModuleRevisionId, BrokenDependency> entry : failuresByRevisionId.entrySet()) {
                Collection<List<ModuleRevisionId>> paths = calculatePaths(entry);
                result.addUnresolvedDependency(new DefaultUnresolvedDependency(entry.getKey(), entry.getValue().failure.withIncomingPaths(paths)));
            }
        }

        private Collection<List<ModuleRevisionId>> calculatePaths(Map.Entry<ModuleRevisionId, BrokenDependency> entry) {
            // Include the shortest path from each version that has a direct dependency on the broken dependency, back to the root
            
            Map<DefaultModuleRevisionResolveState, List<ModuleRevisionId>> shortestPaths = new LinkedHashMap<DefaultModuleRevisionResolveState, List<ModuleRevisionId>>();
            List<ModuleRevisionId> rootPath = new ArrayList<ModuleRevisionId>();
            rootPath.add(root.moduleRevision.id);
            shortestPaths.put(root.moduleRevision, rootPath);

            Set<DefaultModuleRevisionResolveState> directDependees = new LinkedHashSet<DefaultModuleRevisionResolveState>();
            for (ConfigurationNode node : entry.getValue().requiredBy) {
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
                    List<ModuleRevisionId> shortest = null;
                    for (ConfigurationNode configuration : version.configurations) {
                        for (DependencyEdge dependencyEdge : configuration.incomingEdges) {
                            List<ModuleRevisionId> candidate = shortestPaths.get(dependencyEdge.from.moduleRevision);
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
                    List<ModuleRevisionId> path = new ArrayList<ModuleRevisionId>();
                    path.addAll(shortest);
                    path.add(version.id);
                    shortestPaths.put(version, path);
                }
            }

            List<List<ModuleRevisionId>> paths = new ArrayList<List<ModuleRevisionId>>();
            for (DefaultModuleRevisionResolveState version : directDependees) {
                List<ModuleRevisionId> path = shortestPaths.get(version);
                paths.add(path);
            }
            return paths;
        }

        public void addUnresolvedDependency(DependencyEdge dependency, ModuleRevisionId revisionId, ModuleVersionResolveException failure) {
            BrokenDependency breakage = failuresByRevisionId.get(revisionId);
            if (breakage == null) {
                breakage = new BrokenDependency(failure);
                failuresByRevisionId.put(revisionId, breakage);
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
        private final Set<String> targetConfigurationRules;
        private final ResolveState resolveState;
        private final ModuleVersionSpec selectorSpec;
        private final Set<ConfigurationNode> targetConfigurations = new LinkedHashSet<ConfigurationNode>();
        private ModuleVersionSelectorResolveState selector;
        private DefaultModuleRevisionResolveState targetModuleRevision;

        public DependencyEdge(ConfigurationNode from, DependencyDescriptor dependencyDescriptor, Set<String> targetConfigurationRules, ModuleVersionSpec selectorSpec, ResolveState resolveState) {
            this.from = from;
            this.dependencyDescriptor = dependencyDescriptor;
            this.targetConfigurationRules = targetConfigurationRules;
            this.selectorSpec = selectorSpec;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s(%s)", from.toString(), dependencyDescriptor.getDependencyRevisionId(), targetConfigurationRules);
        }

        public DefaultModuleRevisionResolveState getTargetModuleRevision() {
            return targetModuleRevision;
        }

        public void resolveModuleRevisionId() {
            if (targetModuleRevision == null) {
                selector = resolveState.getSelector(dependencyDescriptor);
                targetModuleRevision = selector.resolveModuleRevisionId();
                selector.module.addUnattachedDependency(this);
            }
        }

        public boolean isTransitive() {
            return from.isTransitive() && dependencyDescriptor.isTransitive();
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
                selector.module.removeUnattachedDependency(this);
            }
        }

        public void removeFromTargetConfigurations() {
            for (ConfigurationNode targetConfiguration : targetConfigurations) {
                targetConfiguration.removeIncomingEdge(this);
            }
            targetConfigurations.clear();
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            selector = selector.restart(selected);
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetConfigurations.clear();
            ModuleDescriptor targetDescriptor = targetModuleRevision.getDescriptor();
            if (targetDescriptor == null) {
                // Broken version
                return;
            }

            IvyNode node = new IvyNode(resolveState.resolveData, targetDescriptor);
            Set<String> targets = new LinkedHashSet<String>();
            for (String targetConfiguration : targetConfigurationRules) {
                Collections.addAll(targets, node.getRealConfs(targetConfiguration));
            }

            for (String targetConfigurationName : targets) {
                // TODO - this is the wrong spot for this check
                if (targetDescriptor.getConfiguration(targetConfigurationName) == null) {
                    throw new RuntimeException(String.format("Module version group:%s, module:%s, version:%s, configuration:%s declares a dependency on configuration '%s' which is not declared in the module descriptor for group:%s, module:%s, version:%s",
                            from.moduleRevision.id.getOrganisation(), from.moduleRevision.id.getName(), from.moduleRevision.id.getRevision(), from.configurationName,
                            targetConfigurationName, targetModuleRevision.id.getOrganisation(), targetModuleRevision.id.getName(), targetModuleRevision.id.getRevision()));
                }
                ConfigurationNode targetConfiguration = resolveState.getConfigurationNode(targetModuleRevision, targetConfigurationName);
                targetConfigurations.add(targetConfiguration);
            }
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationNode childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory) {
            String[] targetConfigurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            DependencyArtifactDescriptor[] dependencyArtifacts = dependencyDescriptor.getDependencyArtifacts(targetConfigurations);
            if (dependencyArtifacts.length == 0) {
                return Collections.emptySet();
            }
            Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
            for (DependencyArtifactDescriptor artifactDescriptor : dependencyArtifacts) {
                MDArtifact artifact = new MDArtifact(childConfiguration.descriptor, artifactDescriptor.getName(), artifactDescriptor.getType(), artifactDescriptor.getExt(), artifactDescriptor.getUrl(), artifactDescriptor.getQualifiedExtraAttributes());
                artifacts.add(resolvedArtifactFactory.create(childConfiguration.getResult(), artifact, selector.resolve().getArtifactResolver()));
            }
            return artifacts;
        }

        public void attachToParents(ConfigurationNode childConfiguration, ResolvedArtifactFactory artifactFactory, ResolvedConfigurationBuilder result) {
            DefaultResolvedDependency parent = from.getResult();
            DefaultResolvedDependency child = childConfiguration.getResult();
            parent.addChild(child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration, artifactFactory);
            if (!artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, artifacts);
            }

            if (artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, childConfiguration.getArtifacts(artifactFactory));
            }
            for (ResolvedArtifact artifact : child.getParentArtifacts(parent)) {
                result.addArtifact(artifact);
            }

            if (parent == result.getRoot()) {
                EnhancedDependencyDescriptor enhancedDependencyDescriptor = (EnhancedDependencyDescriptor) dependencyDescriptor;
                result.addFirstLevelDependency(enhancedDependencyDescriptor.getModuleDependency(), child);
            }
        }

        public ModuleVersionSpec getSelector() {
            String[] configurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            ModuleVersionSpec selector = ModuleVersionSpec.forExcludes(dependencyDescriptor.getExcludeRules(configurations));
            return selector.intersect(selectorSpec);
        }

        public boolean isFailed() {
            return selector != null && selector.failure != null;
        }

        public ModuleVersionSelector getRequested() {
            return new DefaultModuleVersionSelector(
                    dependencyDescriptor.getDependencyRevisionId().getOrganisation(),
                    dependencyDescriptor.getDependencyRevisionId().getName(),
                    dependencyDescriptor.getDependencyRevisionId().getRevision());
        }

        public ModuleVersionResolveException getFailure() {
            return selector.failure;
        }

        public ModuleVersionSelection getSelected() {
            return selector.module.selected;
        }

        public void collectFailures(FailureState failureState) {
            if (isFailed()) {
                failureState.addUnresolvedDependency(this, selector.descriptor.getDependencyRevisionId(), getFailure());
            }
        }

    }

    private static class ResolveState {
        private final Map<ModuleId, ModuleResolveState> modules = new LinkedHashMap<ModuleId, ModuleResolveState>();
        private final Map<ResolvedConfigurationIdentifier, ConfigurationNode> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationNode>();
        private final Map<ModuleRevisionId, ModuleVersionSelectorResolveState> selectors = new LinkedHashMap<ModuleRevisionId, ModuleVersionSelectorResolveState>();
        private final ConfigurationNode root;
        private final DependencyToModuleVersionIdResolver resolver;
        private final ResolveData resolveData;
        private final Set<ConfigurationNode> queued = new HashSet<ConfigurationNode>();
        private final LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();

        public ResolveState(ModuleDescriptor rootModule, String rootConfigurationName, DependencyToModuleVersionIdResolver resolver, ResolveData resolveData) {
            this.resolver = resolver;
            this.resolveData = resolveData;
            DefaultModuleRevisionResolveState rootVersion = getRevision(rootModule.getModuleRevisionId());
            rootVersion.setDescriptor(rootModule);
            root = getConfigurationNode(rootVersion, rootConfigurationName);
            root.moduleRevision.module.select(root.moduleRevision);
        }

        public ModuleResolveState getModule(ModuleId moduleId) {
            ModuleId id = new ModuleId(moduleId.getOrganisation(), moduleId.getName());
            ModuleResolveState module = modules.get(id);
            if (module == null) {
                module = new ModuleResolveState(id, this);
                modules.put(id, module);
            }
            return module;
        }

        public DefaultModuleRevisionResolveState getRevision(ModuleRevisionId moduleRevisionId) {
            ModuleRevisionId id = new ModuleRevisionId(new ModuleId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName()), moduleRevisionId.getRevision());
            return getModule(id.getModuleId()).getVersion(id);
        }

        public Collection<ConfigurationNode> getConfigurationNodes() {
            return nodes.values();
        }

        public ConfigurationNode getConfigurationNode(DefaultModuleRevisionResolveState module, String configurationName) {
            ModuleRevisionId original = module.id;
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(original.getOrganisation(), original.getName(), original.getRevision(), configurationName);
            ConfigurationNode configuration = nodes.get(id);
            if (configuration == null) {
                configuration = new ConfigurationNode(module, module.descriptor, configurationName, this);
                nodes.put(id, configuration);
            }
            return configuration;
        }

        public ModuleVersionSelectorResolveState getSelector(DependencyDescriptor dependencyDescriptor) {
            ModuleRevisionId original = dependencyDescriptor.getDependencyRevisionId();
            ModuleRevisionId selectorId = ModuleRevisionId.newInstance(original.getOrganisation(), original.getName(), original.getRevision());
            ModuleVersionSelectorResolveState resolveState = selectors.get(selectorId);
            if (resolveState == null) {
                resolveState = new ModuleVersionSelectorResolveState(dependencyDescriptor, getModule(selectorId.getModuleId()), resolver, this);
                selectors.put(selectorId, resolveState);
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
        final ModuleId id;
        final Set<DependencyEdge> unattachedDependencies = new LinkedHashSet<DependencyEdge>();
        final Map<ModuleRevisionId, DefaultModuleRevisionResolveState> versions = new LinkedHashMap<ModuleRevisionId, DefaultModuleRevisionResolveState>();
        final ResolveState resolveState;
        DefaultModuleRevisionResolveState selected;

        private ModuleResolveState(ModuleId id, ResolveState resolveState) {
            this.id = id;
            this.resolveState = resolveState;
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

        public DefaultModuleRevisionResolveState getVersion(ModuleRevisionId id) {
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
        final ModuleRevisionId id;
        final ResolveState resolveState;
        final Set<ConfigurationNode> configurations = new LinkedHashSet<ConfigurationNode>();
        List<DependencyDescriptor> dependencies;
        ModuleDescriptor descriptor;
        ModuleState state = ModuleState.New;
        ModuleVersionSelectorResolveState resolver;
        ModuleVersionSelectionReason selectionReason = VersionSelectionReasons.REQUESTED;

        private DefaultModuleRevisionResolveState(ModuleResolveState module, ModuleRevisionId id, ResolveState resolveState) {
            this.module = module;
            this.id = id;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public String getRevision() {
            return id.getRevision();
        }

        public Iterable<DependencyDescriptor> getDependencies() {
            if (dependencies == null) {
                dependencies = Arrays.asList(getDescriptor().getDependencies());
            }
            return dependencies;
        }

        public String getId() {
            return String.format("%s:%s:%s", id.getOrganisation(), id.getName(), id.getRevision());
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            for (ConfigurationNode conflictConfiguration : configurations) {
                conflictConfiguration.restart(selected);
            }
        }

        public void addResolver(ModuleVersionSelectorResolveState resolver) {
            if (this.resolver == null) {
                this.resolver = resolver;
            }
        }

        public ModuleDescriptor getDescriptor() {
            if (descriptor == null) {
                if (resolver == null) {
                    throw new IllegalStateException(String.format("No resolver for %s.", this));
                }
                resolver.resolve();
            }
            return descriptor;
        }

        public void addConfiguration(ConfigurationNode configurationNode) {
            configurations.add(configurationNode);
        }

        public void setDescriptor(ModuleDescriptor descriptor) {
            if (this.descriptor == null) {
                this.descriptor = descriptor;
            }
        }

        public ModuleVersionIdentifier getSelectedId() {
            return new DefaultModuleVersionIdentifier(
                    id.getOrganisation(),
                    id.getName(),
                    id.getRevision());
        }

        public ModuleVersionSelectionReason getSelectionReason() {
            return selectionReason;
        }
    }

    private static class ConfigurationNode {
        final DefaultModuleRevisionResolveState moduleRevision;
        final ResolveState resolveState;
        final DefaultModuleDescriptor descriptor;
        final String configurationName;
        final Set<String> heirarchy = new LinkedHashSet<String>();
        final Set<DependencyEdge> incomingEdges = new LinkedHashSet<DependencyEdge>();
        final Set<DependencyEdge> outgoingEdges = new LinkedHashSet<DependencyEdge>();
        DefaultResolvedDependency result;
        ModuleVersionSpec previousTraversal;
        Set<ResolvedArtifact> artifacts;

        private ConfigurationNode(DefaultModuleRevisionResolveState moduleRevision, ModuleDescriptor descriptor, String configurationName, ResolveState resolveState) {
            this.moduleRevision = moduleRevision;
            this.resolveState = resolveState;
            this.descriptor = (DefaultModuleDescriptor) descriptor;
            this.configurationName = configurationName;
            findAncestors(configurationName, resolveState, heirarchy);
            moduleRevision.addConfiguration(this);
        }

        void findAncestors(String config, ResolveState container, Set<String> ancestors) {
            ancestors.add(config);
            for (String parentConfig : descriptor.getConfiguration(config).getExtends()) {
                ancestors.addAll(container.getConfigurationNode(moduleRevision, parentConfig).heirarchy);
            }
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, configurationName);
        }

        public Set<ResolvedArtifact> getArtifacts(ResolvedArtifactFactory resolvedArtifactFactory) {
            if (artifacts == null) {
                artifacts = new LinkedHashSet<ResolvedArtifact>();
                for (String config : heirarchy) {
                    for (Artifact artifact : descriptor.getArtifacts(config)) {
                        final Artifact artifact1 = DefaultArtifact.cloneWithAnotherMrid(artifact, descriptor.getResolvedModuleRevisionId());
//                        MDArtifact mdArtifact = new (descriptor, artifact.getName(), artifact.getType(), artifact.getExt(), artifact.getUrl(), descriptor.getResolvedModuleRevisionId().getExtraAttributes());
                        artifacts.add(resolvedArtifactFactory.create(getResult(), artifact1, moduleRevision.resolver.resolve().getArtifactResolver()));
                    }
                }
            }
            return artifacts;
        }

        public DefaultResolvedDependency getResult() {
            if (result == null) {
                result = new DefaultResolvedDependency(
                        moduleRevision.id.getOrganisation(),
                        moduleRevision.id.getName(),
                        moduleRevision.id.getRevision(),
                        configurationName);
            }

            return result;
        }

        public boolean isTransitive() {
            return descriptor.getConfiguration(configurationName).isTransitive();
        }

        public void visitOutgoingDependencies(Collection<DependencyEdge> target) {
            // If this configuration's version is in conflict, don't do anything
            // If not traversed before, add all selected outgoing edges
            // If traversed before, and the selected modules have changed, remove previous outgoing edges and add outgoing edges again with
            //    the new selections.
            // If traversed before, and the selected modules have not changed, ignore
            // If none of the incoming edges is transitive, then the node has no outgoing edges

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

            for (DependencyDescriptor dependency : moduleRevision.getDependencies()) {
                ModuleId targetModuleId = dependency.getDependencyRevisionId().getModuleId();
                Set<String> targetConfigurations = getTargetConfigurations(dependency);
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
                if (moduleConfiguration.equals("*") || heirarchy.contains(moduleConfiguration)) {
                    for (String targetConfiguration : dependencyDescriptor.getDependencyConfigurations(moduleConfiguration)) {
                        if (targetConfiguration.equals("*")) {
                            Collections.addAll(targetConfigurations, descriptor.getPublicConfigurationsNames());
                        } else {
                            targetConfigurations.add(targetConfiguration);
                        }
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

        public void attachToParents(ResolvedArtifactFactory artifactFactory, ResolvedConfigurationBuilder result) {
            if (moduleRevision.state != ModuleState.Selected) {
                LOGGER.debug("Ignoring {} as it is not selected.", this);
                return;
            }
            LOGGER.debug("Attaching {} to its parents.", this);
            for (DependencyEdge dependency : incomingEdges) {
                dependency.attachToParents(this, artifactFactory, result);
            }
        }

        public void collectFailures(FailureState failureState) {
            if (moduleRevision.state != ModuleState.Selected) {
                return;
            }
            for (DependencyEdge dependency : outgoingEdges) {
                dependency.collectFailures(failureState);
            }
        }

        private ModuleVersionSpec getSelector(List<DependencyEdge> transitiveEdges) {
            ModuleVersionSpec selector;
            if (transitiveEdges.isEmpty()) {
                selector = ModuleVersionSpec.forExcludes();
            } else {
                selector = transitiveEdges.get(0).getSelector();
                for (int i = 1; i < transitiveEdges.size(); i++) {
                    DependencyEdge dependencyEdge = transitiveEdges.get(i);
                    selector = selector.union(dependencyEdge.getSelector());
                }
            }
            String[] configurations = heirarchy.toArray(new String[heirarchy.size()]);
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

        public void restart(DefaultModuleRevisionResolveState state) {
            // Restarting this configuration after conflict resolution.
            // If this configuration belongs to the select version, queue ourselves up for traversal.
            // If not, then move our incoming edges across to the selected configuration
            if (moduleRevision == state) {
                resolveState.onMoreSelected(this);
            } else {
                for (DependencyEdge dependency : incomingEdges) {
                    dependency.restart(state);
                }
                incomingEdges.clear();
            }
        }

        private ModuleVersionIdentifier toId() {
            return newId(moduleRevision.id.getOrganisation(),
                    moduleRevision.id.getName(),
                    moduleRevision.id.getRevision());
        }
    }

    private static class ModuleVersionSelectorResolveState {
        final DependencyDescriptor descriptor;
        final DependencyToModuleVersionIdResolver resolver;
        final ResolveState resolveState;
        final ModuleResolveState module;
        ModuleVersionResolveException failure;
        DefaultModuleRevisionResolveState targetModuleRevision;
        ModuleVersionIdResolveResult idResolveResult;
        ModuleVersionResolveResult resolveResult;

        private ModuleVersionSelectorResolveState(DependencyDescriptor descriptor, ModuleResolveState module, DependencyToModuleVersionIdResolver resolver, ResolveState resolveState) {
            this.descriptor = descriptor;
            this.module = module;
            this.resolver = resolver;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return descriptor.toString();
        }

        /**
         * @return The module version, or null if there is a failure to resolve this selector.
         */
        public DefaultModuleRevisionResolveState resolveModuleRevisionId() {
            if (targetModuleRevision != null) {
                //(SF) this might not be quite right
                //this.targetModuleRevision might have been evicted in an earlier pass of conflict resolution
                //and the module.selected has the actual target module.
                //I'm not sure how big deal is it.
                return targetModuleRevision;
            }
            if (failure != null) {
                return null;
            }

            idResolveResult = resolver.resolve(descriptor);
            if (idResolveResult.getFailure() != null) {
                failure = idResolveResult.getFailure();
                return null;
            }

            targetModuleRevision = resolveState.getRevision(idResolveResult.getId());
            targetModuleRevision.addResolver(this);

            if (idResolveResult.getSelectionReason() == ModuleVersionIdResolveResult.IdSelectionReason.forced) {
                targetModuleRevision.selectionReason = VersionSelectionReasons.FORCED;
            }

            return targetModuleRevision;
        }

        public ModuleVersionResolveResult resolve() {
            if (resolveResult != null) {
                return resolveResult;
            }
            if (failure != null) {
                return null;
            }

            try {
                resolveResult = idResolveResult.resolve();
                resolveState.getRevision(resolveResult.getId()).setDescriptor(resolveResult.getDescriptor());
            } catch (ModuleVersionResolveException e) {
                failure = e;
            }
            return resolveResult;
        }

        public ModuleVersionSelectorResolveState restart(DefaultModuleRevisionResolveState moduleRevision) {
            return resolveState.getSelector(descriptor.clone(moduleRevision.id));
        }
    }

    private static class ForcedModuleConflictResolver {
        private final ModuleConflictResolver resolver;

        private ForcedModuleConflictResolver(ModuleConflictResolver resolver) {
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
            //TODO SF unit test
            DefaultModuleRevisionResolveState out = (DefaultModuleRevisionResolveState) resolver.select(candidates, root);
            out.selectionReason = VersionSelectionReasons.CONFLICT_RESOLUTION;
            return out;
        }
    }
}
