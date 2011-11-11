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
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.UnresolvedDependency;
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
        ResolveState resolveState = new ResolveState(moduleDescriptor, configuration.getName());
        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.root.getResult());

        traverseGraph(resolveData, resolveState, result);
        assembleResult(resolveState, result);

        return result;
    }

    /**
     * Traverses the dependency graph, resolving conflicts and building the paths from the root configuration.
     */
    private void traverseGraph(ResolveData resolveData, ResolveState resolveState, ResolvedConfigurationBuilder result) {
        SetMultimap<DefaultModuleRevisionResolveState, ResolvePath> pendingConflictResolution = LinkedHashMultimap.create();
        SetMultimap<ModuleId, DefaultModuleRevisionResolveState> allVersions = LinkedHashMultimap.create();
        Set<ModuleId> conflicts = new LinkedHashSet<ModuleId>();

        List<ResolvePath> queue = new ArrayList<ResolvePath>();
        resolveState.root.addOutgoingDependencies(new RootPath(), queue);

        while (!queue.isEmpty() || !pendingConflictResolution.isEmpty()) {
            if (!queue.isEmpty()) {
                ResolvePath path = queue.remove(0);
                LOGGER.debug("Visiting path {}.", path);

                try {
                    path.resolveModuleRevisionId(dependencyResolver, resolveState);
                    ModuleId moduleId = path.getModuleId();

                    if (allVersions.put(moduleId, path.getTargetModuleRevision())) {
                        // A new module revision. Check for conflict
                        Set<DefaultModuleRevisionResolveState> versions = allVersions.get(moduleId);
                        if (versions.size() == 1) {
                            // First version of this module. Select it for now
                            LOGGER.debug("Selecting new module version {}.", path.getTargetModuleRevision());
                            resolveState.select(path.getTargetModuleRevision());
                        } else {
                            // Not the first version of this module. We have a new conflict
                            LOGGER.debug("Found new conflicting module version {}.", path.getTargetModuleRevision());
                            conflicts.add(moduleId);

                            // Deselect the currently selected version, and park all queued paths that traverse any version of the module
                            // for later conflict resolution
                            resolveState.clearSelection(moduleId);
                            Iterator<ResolvePath> iter = queue.iterator();
                            while (iter.hasNext()) {
                                ResolvePath resolvePath = iter.next();
                                DefaultModuleRevisionResolveState conflict = resolvePath.traverses(versions);
                                if (conflict != null) {
                                    LOGGER.debug("Queued path {} traverses version {} with conflicts. Parking this path.", resolvePath, conflict);
                                    iter.remove();
                                    pendingConflictResolution.put(conflict, resolvePath);
                                }
                            }
                        }
                    }

                    if (conflicts.contains(moduleId)) {
                        // This path refers to a conflicted version, park it for later conflict resolution
                        LOGGER.debug("Path refers to module {} with conflicts. Parking this path", moduleId);
                        pendingConflictResolution.put(path.getTargetModuleRevision(), path);
                        continue;
                    }

                    DefaultModuleRevisionResolveState selectedVersion = resolveState.getSelected(moduleId);
                    if (selectedVersion != path.getTargetModuleRevision()) {
                        // This path refers to a version that has been evicted. Restart it, referring to the selected version
                        LOGGER.debug("Version has been evicted. Restarting with target {}.", selectedVersion);
                        queue.add(0, path.restart(selectedVersion));
                        continue;
                    }

                    // This path refers to a selected version - resolve the meta-data for the target version
                    path.resolveMetaData(resolveState);
                } catch (ModuleRevisionResolveException t) {
                    result.addUnresolvedDependency(path.asUnresolvedDependency(t));
                    continue;
                }

                path.addOutgoingDependencies(resolveData, resolveState, queue);
            } else {
                // We have some batched up conflicts. Resolve the first, and continue traversing the graph
                ModuleId moduleId = conflicts.iterator().next();
                conflicts.remove(moduleId);
                Set<DefaultModuleRevisionResolveState> candidates = allVersions.get(moduleId);
                DefaultModuleRevisionResolveState selected = conflictResolver.select(candidates, resolveState.root.moduleRevision);
                LOGGER.debug("Selected {} from conflicting modules {}.", selected, candidates);
                resolveState.select(selected);
                for (ResolvePath path : pendingConflictResolution.removeAll(selected)) {
                    queue.add(path.restart(candidates, selected));
                }
                for (DefaultModuleRevisionResolveState candidate : candidates) {
                    if (candidate != selected) {
                        for (ResolvePath path : pendingConflictResolution.removeAll(candidate)) {
                            queue.add(path.restart(candidates, selected));
                        }
                        for (DependencyResolvePath path : new LinkedHashSet<DependencyResolvePath>(candidate.incomingPaths)) {
                            queue.add(path.restart(candidates, selected));
                        }
                    }
                }
            }
        }
    }

    /**
     * Populates the result from the graph traversal state.
     */
    private void assembleResult(ResolveState resolveState, ResolvedConfigurationBuilder result) {
        for (ConfigurationResolveState resolvedConfiguration : resolveState.getConfigurations()) {
            resolvedConfiguration.attachToParents(resolvedArtifactFactory, artifactResolver, result);
        }
    }

    private static class ResolveState {
        final SetMultimap<ModuleId, DefaultModuleRevisionResolveState> modules = LinkedHashMultimap.create();
        final Map<ModuleId, DefaultModuleRevisionResolveState> selectedVersions = new HashMap<ModuleId, DefaultModuleRevisionResolveState>();
        final Map<ModuleRevisionId, DefaultModuleRevisionResolveState> revisions = new LinkedHashMap<ModuleRevisionId, DefaultModuleRevisionResolveState>();
        final Map<ResolvedConfigurationIdentifier, ConfigurationResolveState> configurations = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationResolveState>();
        final ConfigurationResolveState root;

        public ResolveState(ModuleDescriptor rootModule, String rootConfigurationName) {
            root = getConfiguration(rootModule, rootConfigurationName);
        }

        DefaultModuleRevisionResolveState getRevision(ModuleRevisionId moduleRevisionId) {
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

        DefaultModuleRevisionResolveState getRevision(ModuleDescriptor descriptor) {
            DefaultModuleRevisionResolveState moduleRevision = getRevision(descriptor.getModuleRevisionId());
            if (moduleRevision.descriptor == null) {
                moduleRevision.descriptor = descriptor;
            }
            return moduleRevision;
        }

        ConfigurationResolveState getConfiguration(ModuleDescriptor descriptor, String configurationName) {
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

        public Collection<ConfigurationResolveState> getConfigurations() {
            return configurations.values();
        }

        public void select(DefaultModuleRevisionResolveState revision) {
            ModuleId moduleId = revision.id.getModuleId();
            assert !selectedVersions.containsKey(moduleId);
            selectedVersions.put(moduleId, revision);
            revision.selected = true;
        }

        public void clearSelection(ModuleId moduleId) {
            DefaultModuleRevisionResolveState version = selectedVersions.remove(moduleId);
            if (version != null) {
                version.selected = false;
            }
        }

        public DefaultModuleRevisionResolveState getSelected(ModuleId moduleId) {
            return selectedVersions.get(moduleId);
        }
    }

    private static class DefaultModuleRevisionResolveState implements ModuleRevisionResolveState {
        final ModuleRevisionId id;
        ModuleDescriptor descriptor;
        final Set<DependencyResolvePath> incomingPaths = new LinkedHashSet<DependencyResolvePath>();
        Set<DependencyResolveState> dependencies;
        boolean selected;

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

        public Set<DependencyResolveState> getDependencies() {
            if (dependencies == null) {
                dependencies = new LinkedHashSet<DependencyResolveState>();
                for (DependencyDescriptor dependencyDescriptor : descriptor.getDependencies()) {
                    dependencies.add(new DependencyResolveState(dependencyDescriptor));
                }
            }
            return dependencies;
        }

        public void addIncomingPath(DependencyResolvePath path) {
            incomingPaths.add(path);
        }

        public String getId() {
            return String.format("%s:%s:%s", id.getOrganisation(), id.getName(), id.getRevision());
        }
    }

    private static class ConfigurationResolveState {
        final DefaultModuleRevisionResolveState moduleRevision;
        final ModuleDescriptor descriptor;
        final String configurationName;
        final Set<String> heirarchy = new LinkedHashSet<String>();
        final Set<ResolvePath> incomingPaths = new LinkedHashSet<ResolvePath>();
        DefaultResolvedDependency result;
        Set<ResolvedArtifact> artifacts;

        private ConfigurationResolveState(DefaultModuleRevisionResolveState moduleRevision, ModuleDescriptor descriptor, String configurationName, ResolveState container) {
            this.moduleRevision = moduleRevision;
            this.descriptor = descriptor;
            this.configurationName = configurationName;
            findAncestors(configurationName, container, heirarchy);
        }

        void findAncestors(String config, ResolveState container, Set<String> ancestors) {
            ancestors.add(config);
            for (String parentConfig : descriptor.getConfiguration(config).getExtends()) {
                ancestors.addAll(container.getConfiguration(descriptor, parentConfig).heirarchy);
            }
        }

        void addIncomingPath(DependencyResolvePath path) {
            incomingPaths.add(path);
        }

        void addOutgoingDependencies(ResolvePath incomingPath, Collection<? super DependencyResolvePath> dependencies) {
            if (incomingPath.canReach(this)) {
                LOGGER.debug("Skipping {} as it already traverses {}.", incomingPath, this);
                return;
            }
            for (DependencyResolveState dependency : moduleRevision.getDependencies()) {
                Set<String> targetConfigurations = dependency.getTargetConfigurations(this);
                ModuleId targetModuleId = dependency.descriptor.getDependencyRevisionId().getModuleId();
                if (!targetConfigurations.isEmpty() && !excludes(targetModuleId) && !incomingPath.excludes(targetModuleId)) {
                    DependencyResolvePath dependencyResolvePath = new DependencyResolvePath(incomingPath, this, dependency, targetConfigurations);
                    dependencies.add(dependencyResolvePath);
                }
            }
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", moduleRevision, configurationName);
        }

        public boolean excludes(ModuleId moduleId) {
            String[] configurations = heirarchy.toArray(new String[heirarchy.size()]);
            ArtifactId placeholderArtifact = new ArtifactId(moduleId, "ivy", "ivy", "ivy");
            boolean excluded = descriptor.doesExclude(configurations, placeholderArtifact);
            if (excluded) {
                LOGGER.debug("{} is excluded by {}.", moduleId, this);
                return true;
            }
            return false;
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

        public void attachToParents(ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result) {
            if (moduleRevision.selected) {
                LOGGER.debug("Attaching {} to its parents.", this);
                for (ResolvePath incomingPath : incomingPaths) {
                    incomingPath.attachToParents(this, resolvedArtifactFactory, resolver, result);
                }
            } else {
                LOGGER.debug("Skipping evicted {}.", this);
            }
        }

        public boolean isTransitive() {
            return descriptor.getConfiguration(configurationName).isTransitive();
        }
    }

    private static abstract class ResolvePath {
        public abstract void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result);

        public abstract boolean excludes(ModuleId moduleId);

        public abstract boolean canReach(ConfigurationResolveState configuration);

        public abstract DefaultModuleRevisionResolveState traverses(Collection<DefaultModuleRevisionResolveState> candidates);

        public abstract void buildPathFromRoot(Collection<DefaultModuleRevisionResolveState> modules);

        public abstract void resolveModuleRevisionId(DependencyToModuleResolver dependencyResolver, ResolveState resolveState);

        /**
         * Returns this path with any of the given candidates substituted with the given target. Returns this if no substitutions required.
         * Truncates the path at the first substitution, if a substitution is required.
         */
        public abstract ResolvePath restart(Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState moduleRevision);

        public abstract ResolvePath restart(DefaultModuleRevisionResolveState selectedVersion);

        public abstract ModuleId getModuleId();

        public abstract DefaultModuleRevisionResolveState getTargetModuleRevision();

        public abstract void resolveMetaData(ResolveState resolveState);

        public abstract UnresolvedDependency asUnresolvedDependency(ModuleRevisionResolveException t);

        public abstract void addOutgoingDependencies(ResolveData resolveData, ResolveState resolveState, List<ResolvePath> queue);
    }

    private static class RootPath extends ResolvePath {
        @Override
        public String toString() {
            return "<root>";
        }

        @Override
        public ModuleId getModuleId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DefaultModuleRevisionResolveState getTargetModuleRevision() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addOutgoingDependencies(ResolveData resolveData, ResolveState resolveState, List<ResolvePath> queue) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnresolvedDependency asUnresolvedDependency(ModuleRevisionResolveException t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveMetaData(ResolveState resolveState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resolveModuleRevisionId(DependencyToModuleResolver dependencyResolver, ResolveState resolveState) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvePath restart(DefaultModuleRevisionResolveState selectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean excludes(ModuleId moduleId) {
            return false;
        }

        @Override
        public boolean canReach(ConfigurationResolveState configuration) {
            return false;
        }

        @Override
        public DefaultModuleRevisionResolveState traverses(Collection<DefaultModuleRevisionResolveState> candidates) {
            return null;
        }

        @Override
        public void buildPathFromRoot(Collection<DefaultModuleRevisionResolveState> modules) {
        }

        @Override
        public ResolvePath restart(Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState moduleRevision) {
            return this;
        }

        @Override
        public void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result) {
            // Don't need to do anything
        }
    }

    private static class DependencyResolveState {
        final DependencyDescriptor descriptor;
        DefaultModuleRevisionResolveState targetModuleRevision;
        ModuleVersionResolver resolver;
        boolean resolved;

        private DependencyResolveState(DependencyDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return descriptor.toString();
        }

        public void resolveModuleRevisionId(DependencyToModuleResolver resolver, ResolveState resolveState) {
            if (targetModuleRevision != null) {
                return;
            }

            this.resolver = resolver.create(descriptor);
            targetModuleRevision = resolveState.getRevision(this.resolver.getId());
        }

        public void resolve(ResolveState resolveState) {
            if (resolved) {
                return;
            }

            resolveState.getRevision(resolver.getDescriptor());
            resolved = true;
        }

        public Set<String> getTargetConfigurations(ConfigurationResolveState fromConfiguration) {
            Set<String> targetConfigurations = new LinkedHashSet<String>();
            for (String moduleConfiguration : descriptor.getModuleConfigurations()) {
                if (moduleConfiguration.equals("*") || fromConfiguration.heirarchy.contains(moduleConfiguration)) {
                    for (String targetConfiguration : descriptor.getDependencyConfigurations(moduleConfiguration)) {
                        if (targetConfiguration.equals("*")) {
                            Collections.addAll(targetConfigurations, fromConfiguration.descriptor.getPublicConfigurationsNames());
                        } else {
                            targetConfigurations.add(targetConfiguration);
                        }
                    }
                }
            }
            return targetConfigurations;
        }

        public String getDependencyId() {
            ModuleRevisionId depId = descriptor.getDependencyRevisionId();
            return String.format("%s:%s:%s", depId.getOrganisation(), depId.getName(), depId.getRevision());
        }

        public boolean isTransitive() {
            return descriptor.isTransitive();
        }
    }

    private static class DependencyResolvePath extends ResolvePath {
        final ResolvePath path;
        final ConfigurationResolveState from;
        final Set<String> targetConfigurations;
        final DependencyResolveState dependency;
        DefaultModuleRevisionResolveState targetModuleRevision;
        boolean needMetaData = true;

        private DependencyResolvePath(ResolvePath path, ConfigurationResolveState from, DependencyResolveState dependency, Set<String> targetConfigurations) {
            this.path = path;
            this.from = from;
            this.dependency = dependency;
            this.targetConfigurations = targetConfigurations;
        }

        @Override
        public String toString() {
            return String.format("%s | %s -> %s(%s)", path, from, dependency.descriptor.getDependencyRevisionId(), targetConfigurations);
        }

        @Override
        public ModuleId getModuleId() {
            return targetModuleRevision.id.getModuleId();
        }

        @Override
        public DefaultModuleRevisionResolveState getTargetModuleRevision() {
            return targetModuleRevision;
        }

        @Override
        public UnresolvedDependency asUnresolvedDependency(ModuleRevisionResolveException t) {
            return new DefaultUnresolvedDependency(dependency.descriptor.getDependencyRevisionId().toString(), t);
        }

        /**
         * Resolves the module revision id for this path.
         */
        public void resolveModuleRevisionId(DependencyToModuleResolver resolver, ResolveState resolveState) {
            if (targetModuleRevision == null) {
                try {
                    dependency.resolveModuleRevisionId(resolver, resolveState);
                } catch (ModuleRevisionResolveException e) {
                    throw wrap(e);
                }
                referTo(dependency.targetModuleRevision);
            }
        }
        
        /**
         * Resolves the meta-data for this path.
         */
        public void resolveMetaData(ResolveState resolveState) {
            if (!needMetaData) {
                return;
            }
            try {
                dependency.resolve(resolveState);
            } catch (ModuleRevisionResolveException e) {
                throw wrap(e);
            }
        }

        private ModuleRevisionResolveException wrap(ModuleRevisionResolveException e) {
            if (e instanceof ModuleRevisionNotFoundException) {
                Formatter formatter = new Formatter();
                formatter.format("Module %s not found. It is required by:", dependency.getDependencyId());
                Set<DefaultModuleRevisionResolveState> modules = new LinkedHashSet<DefaultModuleRevisionResolveState>();
                buildPathFromRoot(modules);
                for (DefaultModuleRevisionResolveState module : modules) {
                    formatter.format("%n    %s", module.getId());
                }
                return new ModuleRevisionNotFoundException(formatter.toString());
            }
            return e;
        }

        private void referTo(DefaultModuleRevisionResolveState targetModuleRevision) {
            this.targetModuleRevision = targetModuleRevision;
            targetModuleRevision.addIncomingPath(this);
        }

        @Override
        public void addOutgoingDependencies(ResolveData resolveData, ResolveState resolveState, List<ResolvePath> queue) {
            ModuleDescriptor targetDescriptor = targetModuleRevision.descriptor;
            if (targetDescriptor == null) {
                throw new IllegalStateException(String.format("No descriptor for %s.", targetModuleRevision));
            }

            IvyNode node = new IvyNode(resolveData, targetDescriptor);
            Set<String> targets = new LinkedHashSet<String>();
            for (String targetConfiguration : targetConfigurations) {
                Collections.addAll(targets, node.getRealConfs(targetConfiguration));
            }

            for (String targetConfigurationName : targets) {
                ConfigurationResolveState targetConfiguration = resolveState.getConfiguration(targetDescriptor, targetConfigurationName);
                LOGGER.debug("{} is outgoing to {}.", this, targetConfiguration);
                targetConfiguration.addIncomingPath(this);
                if (from.isTransitive() && dependency.isTransitive()) {
                    targetConfiguration.addOutgoingDependencies(this, queue);
                }
            }
        }

        @Override
        public boolean excludes(ModuleId moduleId) {
            String[] configurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            ArtifactId placeholderArtifact = new ArtifactId(moduleId, "ivy", "ivy", "ivy");
            boolean excluded = dependency.descriptor.doesExclude(configurations, placeholderArtifact);
            if (excluded) {
                LOGGER.debug("{} is excluded by {}.", moduleId, this);
                return true;
            }

            excluded = from.excludes(moduleId);
            if (excluded) {
                return true;
            }

            return path.excludes(moduleId);
        }

        @Override
        public void buildPathFromRoot(Collection<DefaultModuleRevisionResolveState> modules) {
            modules.add(from.moduleRevision);
            path.buildPathFromRoot(modules);
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
        public ResolvePath restart(Collection<DefaultModuleRevisionResolveState> candidates, DefaultModuleRevisionResolveState moduleRevision) {
            ResolvePath newParent = path.restart(candidates, moduleRevision);
            if (newParent != path) {
                // Parent path has changed - discard this and return new parent
                if (targetModuleRevision != null) {
                    targetModuleRevision.incomingPaths.remove(this);
                }
                return newParent;
            }
            if (targetModuleRevision == null) {
                // Need to substitute this path for a new one
                return restart(moduleRevision);
            }

            if (targetModuleRevision == moduleRevision || !candidates.contains(targetModuleRevision)) {
                // Parent path has not changed and this path does not need to be substituted - return this
                return this;
            }

            // Need to substitute this path for a new one
            return restart(moduleRevision);
        }

        @Override
        public ResolvePath restart(DefaultModuleRevisionResolveState moduleRevision) {
            LOGGER.debug("Restarting {} on conflict, now refers to {}.", this, moduleRevision);
            if (targetModuleRevision != null) {
                targetModuleRevision.incomingPaths.remove(this);
            }
            DependencyResolvePath newPath = new DependencyResolvePath(path, from, dependency, targetConfigurations);
            newPath.referTo(moduleRevision);
            newPath.needMetaData = false;
            return newPath;
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver) {
            String[] targetConfigurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            DependencyArtifactDescriptor[] dependencyArtifacts = dependency.descriptor.getDependencyArtifacts(targetConfigurations);
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

        @Override
        public void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result) {
            DefaultResolvedDependency parent = from.getResult();
            DefaultResolvedDependency child = childConfiguration.getResult();
            parent.addChild(child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration, resolvedArtifactFactory, resolver);
            if (!artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, artifacts);
            }

            if (artifacts.isEmpty()) {
                child.addParentSpecificArtifacts(parent, childConfiguration.getArtifacts(resolvedArtifactFactory, resolver));
            }
            for (ResolvedArtifact artifact : child.getParentArtifacts(parent)) {
                result.addArtifact(artifact);
            }

            if (parent == result.getRoot()) {
                EnhancedDependencyDescriptor enhancedDependencyDescriptor = (EnhancedDependencyDescriptor) dependency.descriptor;
                result.addFirstLevelDependency(enhancedDependencyDescriptor.getModuleDependency(), child);
            }
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
                    if (incomingPath.from.moduleRevision == root && incomingPath.dependency.descriptor.isForce()) {
                        return candidate;
                    }
                }
            }
            return (DefaultModuleRevisionResolveState) resolver.select(candidates, root);
        }
    }
}
