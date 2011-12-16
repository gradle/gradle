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

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class DependencyGraphBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyGraphBuilder.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final DependencyToModuleResolver dependencyResolver;
    private final ArtifactToFileResolver artifactResolver;
    private final ForcedModuleConflictResolver conflictResolver;

    public DependencyGraphBuilder(ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver artifactResolver, DependencyToModuleResolver dependencyResolver, ModuleConflictResolver conflictResolver) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.artifactResolver = artifactResolver;
        this.dependencyResolver = dependencyResolver;
        this.conflictResolver = new ForcedModuleConflictResolver(conflictResolver);
    }

    public DefaultLenientConfiguration resolve(ConfigurationInternal configuration, ResolveData resolveData) throws ResolveException {
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configuration.getAll(), configuration.getModule());

        ResolveState resolveState = new ResolveState(moduleDescriptor, configuration.getName(), dependencyResolver, resolveData);
        traverseGraph(resolveState);

        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.root.getResult());
        assembleResult(resolveState, result);

        return result;
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(ResolveState resolveState) {
        SetMultimap<ModuleId, DefaultModuleRevisionResolveState> allVersions = LinkedHashMultimap.create();
        Set<ModuleId> conflicts = new LinkedHashSet<ModuleId>();

        resolveState.queue.add(resolveState.root);

        List<DependencyEdge> dependencies = new ArrayList<DependencyEdge>();
        while (!resolveState.queue.isEmpty() || !conflicts.isEmpty()) {
            if (!resolveState.queue.isEmpty()) {
                ConfigurationNode node = resolveState.queue.remove(0);
                System.out.println("=> Visiting configuration " + node);

                // Calculate the outgoing edges of this configuration
                dependencies.clear();
                node.visitOutgoingDependencies(dependencies);

                for (DependencyEdge dependency : dependencies) {
                    System.out.println("* Visiting dependency " + dependency);

                    // Resolve dependency to a particular revision
                    dependency.resolveModuleRevisionId();
                    ModuleId moduleId = dependency.getModuleId();
                    DefaultModuleRevisionResolveState moduleRevision = dependency.getTargetModuleRevision();

                    // Check for a new conflict
                    if (allVersions.put(moduleId, moduleRevision)) {
                        // A new module revision. Check for conflict
                        Set<DefaultModuleRevisionResolveState> versions = allVersions.get(moduleId);
                        if (versions.size() == 1) {
                            // First version of this module. Select it for now
                            System.out.println("  Selecting new module version " + moduleRevision);
                            resolveState.select(moduleRevision);
                        } else {
                            // Not the first version of this module. We have a new conflict
                            System.out.println("  Found new conflicting module version " + moduleRevision);
                            conflicts.add(moduleId);

                            // Deselect the currently selected version, and remove all outgoing edges from the version
                            // This will propagate through the graph and prune configurations that are no longer required
                            DefaultModuleRevisionResolveState previouslySelected = resolveState.clearSelection(moduleId);
                            if (previouslySelected != null) {
                                System.out.println("  Removing outgoing edges from " + previouslySelected);
                                for (ConfigurationNode configuration : previouslySelected.configurations) {
                                    System.out.println("  * removing " + configuration);
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
                Set<DefaultModuleRevisionResolveState> candidates = allVersions.get(moduleId);
                DefaultModuleRevisionResolveState selected = conflictResolver.select(candidates, resolveState.root.moduleRevision);
                System.out.println("=> Selected " + selected + " from conflicting modules " + candidates);
                resolveState.select(selected);

                // Restart each configuration. For the evicted configuration, this means moving incoming dependencies across to the
                // matching selected configuration. For the select configuration, this mean traversing its dependencies.
                for (DefaultModuleRevisionResolveState candidate : candidates) {
                    candidate.restart(selected);
                    for (ConfigurationNode conflictConfiguration : candidate.configurations) {
                        System.out.println("  * restarting " + conflictConfiguration);
                        conflictConfiguration.restart(selected);
                    }
                }
            }
        }
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, ResolvedConfigurationBuilder result) {
        for (ConfigurationNode resolvedConfiguration : resolveState.getConfigurationNodes()) {
            resolvedConfiguration.attachToParents(resolvedArtifactFactory, artifactResolver, result);
        }
        for (ModuleVersionSelectorResolveState selector : resolveState.getSelectors()) {
            selector.addUnresolvedDependency(result);
        }
    }

    private static class DependencyEdge {
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

        public ModuleId getModuleId() {
            return targetModuleRevision.id.getModuleId();
        }

        public DefaultModuleRevisionResolveState getTargetModuleRevision() {
            return targetModuleRevision;
        }

        public void resolveModuleRevisionId() {
            if (targetModuleRevision == null) {
                selector = resolveState.getSelector(dependencyDescriptor);
                selector.resolveModuleRevisionId();
                targetModuleRevision = selector.targetModuleRevision;
            }
        }

        public boolean isTransitive() {
            return from.isTransitive() && dependencyDescriptor.isTransitive();
        }

        public void attachToTargetConfigurations() {
            if (!targetModuleRevision.selected) {
                System.out.println("   refers to unselected revision " + this);
                targetModuleRevision.addUnattachedDependency(this);
                return;
            }
            calculateTargetConfigurations();
            for (ConfigurationNode targetConfiguration : targetConfigurations) {
                targetConfiguration.addIncomingEdge(this);
            }
        }

        public void removeFromTargetConfigurations() {
            for (ConfigurationNode targetConfiguration : targetConfigurations) {
                targetConfiguration.removeIncomingEdge(this);
            }
            targetConfigurations.clear();
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            System.out.println("    -> restarting " + this + " to point to " + selected);
            targetModuleRevision = selected;
            attachToTargetConfigurations();
        }

        private void calculateTargetConfigurations() {
            targetConfigurations.clear();
            ModuleDescriptor targetDescriptor = targetModuleRevision.getDescriptor();
            if (targetDescriptor == null) {
                throw new IllegalStateException(String.format("No descriptor for %s.", targetModuleRevision));
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
                ConfigurationNode targetConfiguration = resolveState.getConfigurationNode(targetDescriptor, targetConfigurationName);
                targetConfigurations.add(targetConfiguration);
            }
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationNode childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver) {
            String[] targetConfigurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            DependencyArtifactDescriptor[] dependencyArtifacts = dependencyDescriptor.getDependencyArtifacts(targetConfigurations);
            if (dependencyArtifacts.length == 0) {
                return Collections.emptySet();
            }
            Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();
            for (DependencyArtifactDescriptor artifactDescriptor : dependencyArtifacts) {
                MDArtifact artifact = new MDArtifact(childConfiguration.descriptor, artifactDescriptor.getName(), artifactDescriptor.getType(), artifactDescriptor.getExt(), artifactDescriptor.getUrl(), artifactDescriptor.getQualifiedExtraAttributes());
                artifacts.add(resolvedArtifactFactory.create(childConfiguration.getResult(), artifact, resolver));
            }
            return artifacts;
        }

        public void attachToParents(ConfigurationNode childConfiguration, ResolvedArtifactFactory artifactFactory, ArtifactToFileResolver artifactResolver, ResolvedConfigurationBuilder result) {
            DefaultResolvedDependency parent = from.getResult();
            DefaultResolvedDependency child = childConfiguration.getResult();
            parent.addChild(child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration, artifactFactory, artifactResolver);
            if (!artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, artifacts);
            }

            if (artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, childConfiguration.getArtifacts(artifactFactory, artifactResolver));
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
    }

    private static class ResolveState {
        private final SetMultimap<ModuleId, DefaultModuleRevisionResolveState> modules = LinkedHashMultimap.create();
        private final Map<ModuleId, DefaultModuleRevisionResolveState> selectedVersions = new HashMap<ModuleId, DefaultModuleRevisionResolveState>();
        private final Map<ModuleRevisionId, DefaultModuleRevisionResolveState> revisions = new LinkedHashMap<ModuleRevisionId, DefaultModuleRevisionResolveState>();
        private final Map<ResolvedConfigurationIdentifier, ConfigurationResolveState> configurations = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationResolveState>();
        private final Map<ResolvedConfigurationIdentifier, ConfigurationNode> nodes = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationNode>();
        private final Map<ModuleRevisionId, ModuleVersionSelectorResolveState> selectors = new LinkedHashMap<ModuleRevisionId, ModuleVersionSelectorResolveState>();
        private final ConfigurationNode root;
        private final DependencyToModuleResolver resolver;
        private final ResolveData resolveData;
        private final LinkedList<ConfigurationNode> queue = new LinkedList<ConfigurationNode>();

        public ResolveState(ModuleDescriptor rootModule, String rootConfigurationName, DependencyToModuleResolver resolver, ResolveData resolveData) {
            this.resolver = resolver;
            this.resolveData = resolveData;
            root = getConfigurationNode(rootModule, rootConfigurationName);
            select(root.moduleRevision);
        }

        public DefaultModuleRevisionResolveState getRevision(ModuleRevisionId moduleRevisionId) {
            ModuleRevisionId id = new ModuleRevisionId(new ModuleId(moduleRevisionId.getOrganisation(), moduleRevisionId.getName()), moduleRevisionId.getRevision());
            DefaultModuleRevisionResolveState moduleRevision = revisions.get(id);
            if (moduleRevision == null) {
                moduleRevision = new DefaultModuleRevisionResolveState(id);
                revisions.put(id, moduleRevision);
                ModuleId moduleId = id.getModuleId();
                modules.put(moduleId, moduleRevision);
            }

            return moduleRevision;
        }

        public DefaultModuleRevisionResolveState getRevision(ModuleDescriptor descriptor) {
            DefaultModuleRevisionResolveState moduleRevision = getRevision(descriptor.getModuleRevisionId());
            if (moduleRevision.descriptor == null) {
                moduleRevision.descriptor = descriptor;
            }
            return moduleRevision;
        }

        public Collection<ConfigurationNode> getConfigurationNodes() {
            return nodes.values();
        }

        public ConfigurationNode getConfigurationNode(ModuleDescriptor descriptor, String configurationName) {
            ModuleRevisionId original = descriptor.getModuleRevisionId();
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(original.getOrganisation(), original.getName(), original.getRevision(), configurationName);
            ConfigurationNode configuration = nodes.get(id);
            if (configuration == null) {
                DefaultModuleRevisionResolveState moduleRevision = getRevision(descriptor);
                configuration = new ConfigurationNode(moduleRevision, descriptor, configurationName, this);
                nodes.put(id, configuration);
            }
            return configuration;
        }

        public ConfigurationResolveState getConfiguration(ModuleDescriptor descriptor, String configurationName) {
            ModuleRevisionId original = descriptor.getModuleRevisionId();
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(original.getOrganisation(), original.getName(), original.getRevision(), configurationName);
            ConfigurationResolveState configuration = configurations.get(id);
            if (configuration == null) {
                DefaultModuleRevisionResolveState moduleRevision = getRevision(descriptor);
                configuration = new ConfigurationResolveState(moduleRevision, descriptor, configurationName, this);
                configurations.put(id, configuration);
            }
            return configuration;
        }

        public ModuleVersionSelectorResolveState getSelector(DependencyDescriptor dependencyDescriptor) {
            ModuleRevisionId original = dependencyDescriptor.getDependencyRevisionId();
            ModuleRevisionId selectorId = ModuleRevisionId.newInstance(original.getOrganisation(), original.getName(), original.getRevision());
            ModuleVersionSelectorResolveState resolveState = selectors.get(selectorId);
            if (resolveState == null) {
                resolveState = new ModuleVersionSelectorResolveState(dependencyDescriptor, resolver.create(dependencyDescriptor), this);
                selectors.put(selectorId, resolveState);
            }
            return resolveState;
        }

        public Collection<ModuleVersionSelectorResolveState> getSelectors() {
            return selectors.values();
        }

        public void select(DefaultModuleRevisionResolveState revision) {
            ModuleId moduleId = revision.id.getModuleId();
            assert !selectedVersions.containsKey(moduleId);
            selectedVersions.put(moduleId, revision);
            revision.selected = true;
        }

        public DefaultModuleRevisionResolveState clearSelection(ModuleId moduleId) {
            DefaultModuleRevisionResolveState version = selectedVersions.remove(moduleId);
            if (version != null) {
                version.selected = false;
            }
            return version;
        }

        /**
         * Called when a change is made to a ConfigurationNode, such that its dependency graph <em>may</em> now be larger than it previously was, and the node should be visited.
         */
        public void onMoreSelected(ConfigurationNode configuration) {
            // Add to the end of the queue, so that we traverse the graph in breadth-wise order to pick up as many conflicts as
            // possible before attempting to resolve them
            queue.addLast(configuration);
        }

        /**
         * Called when a change is made to a ConfigurationNode, such that its dependency graph <em>may</em> now be smaller than it previously was, and the node should be visited.
         */
        public void onFewerSelected(ConfigurationNode configuration) {
            // Add to the front of the queue, to flush out configurations that are no longer required.
            queue.addFirst(configuration);
        }
    }

    private static class DefaultModuleRevisionResolveState implements ModuleRevisionResolveState {
        final ModuleRevisionId id;
        final Set<DependencyResolvePath> incomingPaths = new LinkedHashSet<DependencyResolvePath>();
        final Set<ConfigurationNode> configurations = new LinkedHashSet<ConfigurationNode>();
        final Set<DependencyEdge> unattachedDependencies = new LinkedHashSet<DependencyEdge>();
        List<DependencyDescriptor> dependencies;
        ModuleDescriptor descriptor;
        boolean selected;
        private ModuleVersionSelectorResolveState resolver;

        private DefaultModuleRevisionResolveState(ModuleRevisionId id) {
            this.id = id;
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

        public void addIncomingPath(DependencyResolvePath path) {
            incomingPaths.add(path);
        }

        public void removeIncomingPath(DependencyResolvePath path) {
            incomingPaths.remove(path);
        }

        public String getId() {
            return String.format("%s:%s:%s", id.getOrganisation(), id.getName(), id.getRevision());
        }

        public void addUnattachedDependency(DependencyEdge dependency) {
            unattachedDependencies.add(dependency);
        }

        public void restart(DefaultModuleRevisionResolveState selected) {
            for (DependencyEdge dependency : unattachedDependencies) {
                dependency.restart(selected);
            }
            unattachedDependencies.clear();
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
    }

    private static class ConfigurationNode {
        final DefaultModuleRevisionResolveState moduleRevision;
        private final ResolveState resolveState;
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
            moduleRevision.configurations.add(this);
        }

        void findAncestors(String config, ResolveState container, Set<String> ancestors) {
            ancestors.add(config);
            for (String parentConfig : descriptor.getConfiguration(config).getExtends()) {
                ancestors.addAll(container.getConfiguration(descriptor, parentConfig).heirarchy);
            }
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, configurationName);
        }

        public Set<ResolvedArtifact> getArtifacts(ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver) {
            if (artifacts == null) {
                artifacts = new LinkedHashSet<ResolvedArtifact>();
                for (String config : heirarchy) {
                    for (Artifact artifact : descriptor.getArtifacts(config)) {
                        artifacts.add(resolvedArtifactFactory.create(getResult(), artifact, resolver));
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

            if (!moduleRevision.selected) {
                System.out.println("    -> version is not selected " + this + ". ignoring.");
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
                System.out.println("    -> has no incoming edges " + this + ". ignoring.");
                return;
            }

            ModuleVersionSpec selectorSpec = getSelector(transitiveIncoming);
            if (previousTraversal != null) {
                if (previousTraversal.acceptsSameModulesAs(selectorSpec)) {
                    System.out.println("    -> same modules selected by " + this + ". ignoring.");
                    return;
                }
                removeOutgoingEdges();
            }

            for (DependencyDescriptor dependency : moduleRevision.getDependencies()) {
                ModuleId targetModuleId = dependency.getDependencyRevisionId().getModuleId();
                Set<String> targetConfigurations = getTargetConfigurations(dependency);
                if (!targetConfigurations.isEmpty()) {
                    if (!selectorSpec.isSatisfiedBy(targetModuleId)) {
                        System.out.println("    " + targetModuleId + " is excluded from " + this);
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

        public void attachToParents(ResolvedArtifactFactory artifactFactory, ArtifactToFileResolver artifactResolver, ResolvedConfigurationBuilder result) {
            if (!moduleRevision.selected) {
                System.out.println("Ignoring deselected " + this);
                return;
            }
            System.out.println("Attaching " + this + " to its parents.");
            for (DependencyEdge dependency : incomingEdges) {
                dependency.attachToParents(this, artifactFactory, artifactResolver, result);
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
    }

    private static class ConfigurationResolveState extends ConfigurationNode {
        private ConfigurationResolveState(DefaultModuleRevisionResolveState moduleRevision, ModuleDescriptor descriptor, String configurationName, ResolveState container) {
            super(moduleRevision, descriptor, configurationName, container);
        }
    }

    private static abstract class ResolvePath {

        public abstract boolean canReach(ConfigurationResolveState configuration);

        public abstract DefaultModuleRevisionResolveState traverses(Collection<DefaultModuleRevisionResolveState> candidates);

        public abstract void buildPathFromRoot(Collection<ModuleRevisionId> modules);

        /**
         * Returns this path with any of the given candidates substituted with the given target. Returns this if no substitutions required. Truncates the path at the first substitution, if a
         * substitution is required.
         */
        public abstract ResolvePath restart(ResolveState resolveState, Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState moduleRevision);

        public abstract ModuleId getModuleId();

        public abstract boolean isSelected();
    }

    private static class ModuleVersionSelectorResolveState {
        final DependencyDescriptor descriptor;
        final Set<DependencyResolvePath> incomingPaths = new LinkedHashSet<DependencyResolvePath>();
        final ModuleVersionResolver resolver;
        final ResolveState resolveState;
        ModuleVersionResolveException failure;
        DefaultModuleRevisionResolveState targetModuleRevision;
        boolean resolved;

        private ModuleVersionSelectorResolveState(DependencyDescriptor descriptor, ModuleVersionResolver resolver, ResolveState resolveState) {
            this.descriptor = descriptor;
            this.resolver = resolver;
            this.resolveState = resolveState;
        }

        @Override
        public String toString() {
            return descriptor.toString();
        }

        public void resolveModuleRevisionId() {
            if (targetModuleRevision != null) {
                return;
            }
            if (failure != null) {
                throw failure;
            }

            try {
                targetModuleRevision = resolveState.getRevision(this.resolver.getId());
            } catch (ModuleVersionResolveException e) {
                failure = e;
                throw e;
            }
            targetModuleRevision.addResolver(this);
        }

        public void resolve() {
            if (resolved) {
                return;
            }
            if (failure != null) {
                throw failure;
            }

            try {
                resolveState.getRevision(resolver.getDescriptor());
                resolved = true;
            } catch (ModuleVersionResolveException e) {
                failure = e;
                throw e;
            }
        }

        public void addIncomingPath(DependencyResolvePath path) {
            incomingPaths.add(path);
        }

        public ModuleVersionSelectorResolveState restart(ResolveState resolveState, DefaultModuleRevisionResolveState moduleRevision) {
            return resolveState.getSelector(descriptor.clone(moduleRevision.id));
        }

        public void addUnresolvedDependency(ResolvedConfigurationBuilder result) {
            if (failure == null) {
                return;
            }

            Set<List<ModuleRevisionId>> paths = new LinkedHashSet<List<ModuleRevisionId>>();
            for (DependencyResolvePath incomingPath : incomingPaths) {
                if (!incomingPath.isSelected()) {
                    continue;
                }
                if (paths.size() >= 5) {
                    // Just report on the first few paths
                    break;
                }
                List<ModuleRevisionId> path = new ArrayList<ModuleRevisionId>();
                incomingPath.buildPathFromRoot(path);
                paths.add(path);
            }

            if (paths.isEmpty()) {
                LOGGER.debug("Discarding failure for evicted module version {}: {}.", this, failure);
                return;
            }

            result.addUnresolvedDependency(new DefaultUnresolvedDependency(descriptor.getDependencyRevisionId().toString(), failure.withIncomingPaths(paths)));
        }
    }

    private static class DependencyResolvePath extends ResolvePath {
        final ResolvePath path;
        final ConfigurationResolveState from;
        final Set<String> targetConfigurations;
        final ModuleVersionSelectorResolveState selector;
        final DependencyDescriptor dependency;
        DefaultModuleRevisionResolveState targetModuleRevision;

        private DependencyResolvePath(ResolvePath path, ConfigurationResolveState from, ModuleVersionSelectorResolveState selector, DependencyDescriptor dependency, Set<String> targetConfigurations) {
            this.path = path;
            this.from = from;
            this.selector = selector;
            this.dependency = dependency;
            this.targetConfigurations = targetConfigurations;
            selector.addIncomingPath(this);
        }

        @Override
        public String toString() {
            return String.format("%s | %s -> %s(%s)", path, from, dependency.getDependencyRevisionId(), targetConfigurations);
        }

        @Override
        public ModuleId getModuleId() {
            return targetModuleRevision.id.getModuleId();
        }

        private void referTo(DefaultModuleRevisionResolveState targetModuleRevision) {
            this.targetModuleRevision = targetModuleRevision;
            targetModuleRevision.addIncomingPath(this);
        }

        @Override
        public void buildPathFromRoot(Collection<ModuleRevisionId> modules) {
            path.buildPathFromRoot(modules);
            modules.add(from.moduleRevision.id);
        }

        @Override
        public boolean canReach(ConfigurationResolveState configuration) {
            return from.equals(configuration) || path.canReach(configuration);
        }


        public DefaultModuleRevisionResolveState traverses(Collection<DefaultModuleRevisionResolveState> candidates) {
            DefaultModuleRevisionResolveState version = path.traverses(candidates);
            if (version != null) {
                return version;
            }
            if (candidates.contains(targetModuleRevision)) {
                return targetModuleRevision;
            }
            return null;
        }

        @Override
        public ResolvePath restart(ResolveState resolveState, Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState moduleRevision) {
            ResolvePath newParent = path.restart(resolveState, candidates, moduleRevision);
            if (newParent != path) {
                // Parent path has changed - discard this and return new parent
                if (targetModuleRevision != null) {
                    targetModuleRevision.removeIncomingPath(this);
                }
                return newParent;
            }
            if (targetModuleRevision == null) {
                // This has not been resolved yet - return this
                return this;
            }

            if (targetModuleRevision == moduleRevision || !candidates.contains(targetModuleRevision)) {
                // Parent path has not changed and this path does not need to be substituted - return this
                return this;
            }

            // Need to substitute this path for a new one
            return restart(resolveState, moduleRevision);
        }

        private ResolvePath restart(ResolveState resolveState, DefaultModuleRevisionResolveState moduleRevision) {
            LOGGER.debug("Restarting {} on conflict, now refers to {}.", this, moduleRevision);
            if (targetModuleRevision != null) {
                targetModuleRevision.removeIncomingPath(this);
            }
            ModuleVersionSelectorResolveState newSelector = selector.restart(resolveState, moduleRevision);
            DependencyResolvePath newPath = new DependencyResolvePath(path, from, newSelector, dependency, targetConfigurations);
            newPath.referTo(moduleRevision);
            return newPath;
        }

        public boolean isSelected() {
            if (targetModuleRevision == null) {
                return false;
            }
            if (!targetModuleRevision.selected) {
                return false;
            }
            return path.isSelected();
        }
    }

    private static class ForcedModuleConflictResolver {
        private final ModuleConflictResolver resolver;

        private ForcedModuleConflictResolver(ModuleConflictResolver resolver) {
            this.resolver = resolver;
        }

        DefaultModuleRevisionResolveState select(Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState root) {
            for (DefaultModuleRevisionResolveState candidate : candidates) {
                for (DependencyResolvePath incomingPath : candidate.incomingPaths) {
                    if (incomingPath.from.moduleRevision == root && incomingPath.dependency.isForce()) {
                        return candidate;
                    }
                }
            }
            return (DefaultModuleRevisionResolveState) resolver.select(candidates, root);
        }
    }
}
