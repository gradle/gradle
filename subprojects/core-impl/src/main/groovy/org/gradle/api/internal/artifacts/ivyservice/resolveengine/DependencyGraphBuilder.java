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
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
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

    public DependencyGraphBuilder(ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory) {
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
    }

    public DefaultLenientConfiguration resolve(ConfigurationInternal configuration, ResolveData resolveData, DependencyToModuleResolver dependencyResolver, ArtifactToFileResolver artifactResolver, ModuleConflictResolver conflictResolver) throws ResolveException {
        ForcedModuleConflictResolver outerConflictResolver = new ForcedModuleConflictResolver(conflictResolver);

        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configuration.getAll(), configuration.getModule());
        ResolveState resolveState = new ResolveState(moduleDescriptor, configuration.getName());
        DefaultLenientConfiguration result = new DefaultLenientConfiguration(configuration, resolveState.root.getResult());

        resolve(dependencyResolver, result, resolveState, resolveData, artifactResolver, outerConflictResolver, resolvedArtifactFactory);

        return result;
    }

    private void resolve(DependencyToModuleResolver dependencyResolver, ResolvedConfigurationBuilder result, ResolveState resolveState, ResolveData resolveData, ArtifactToFileResolver artifactResolver, ForcedModuleConflictResolver conflictResolver, ResolvedArtifactFactory resolvedArtifactFactory) {
        SetMultimap<ModuleId, DependencyResolvePath> conflicts = LinkedHashMultimap.create();

        List<DependencyResolvePath> queue = new ArrayList<DependencyResolvePath>();
        resolveState.root.addOutgoingDependencies(new RootPath(), queue);

        while (!queue.isEmpty() || !conflicts.isEmpty()) {
            if (queue.isEmpty()) {
                ModuleId moduleId = conflicts.keySet().iterator().next();
                Set<ModuleRevisionResolveState> candidates = resolveState.getRevisions(moduleId);
                ModuleRevisionResolveState selected = conflictResolver.select(candidates, resolveState.root.moduleRevision);
                LOGGER.debug("Selected {} from conflicting modules {}.", selected, candidates);
                selected.status = Status.Include;
                for (ModuleRevisionResolveState candidate : candidates) {
                    if (candidate != selected) {
                        candidate.status = Status.Evict;
                        for (DependencyResolvePath path : new LinkedHashSet<DependencyResolvePath>(candidate.incomingPaths)) {
                            path.restart(selected, queue);
                        }
                    }
                }
                Set<DependencyResolvePath> paths = conflicts.removeAll(moduleId);
                for (DependencyResolvePath path : paths) {
                    path.restart(selected, queue);
                }
                continue;
            }

            DependencyResolvePath path = queue.remove(0);
            LOGGER.debug("Visiting path {}.", path);

            try {
                path.resolve(dependencyResolver, resolveState);
            } catch (Throwable t) {
                result.addUnresolvedDependency(new DefaultUnresolvedDependency(path.dependency.descriptor.getDependencyRevisionId().toString(), t));
                continue;
            }

            if (path.targetModuleRevision.status == Status.Conflict) {
                LOGGER.debug("Found a conflict. Park this path.");
                conflicts.put(path.targetModuleRevision.id.getModuleId(), path);
            } else {
                path.addOutgoingDependencies(resolveData, resolveState, queue);
            }
        }

        for (ConfigurationResolveState resolvedConfiguration : resolveState.getConfigurations()) {
            resolvedConfiguration.attachToParents(resolvedArtifactFactory, artifactResolver, result);
        }
    }

    private static class ResolveState {
        final SetMultimap<ModuleId, ModuleRevisionResolveState> modules = LinkedHashMultimap.create();
        final Map<ModuleRevisionId, ModuleRevisionResolveState> revisions = new LinkedHashMap<ModuleRevisionId, ModuleRevisionResolveState>();
        final Map<ResolvedConfigurationIdentifier, ConfigurationResolveState> configurations = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationResolveState>();
        final ConfigurationResolveState root;

        public ResolveState(ModuleDescriptor rootModule, String rootConfigurationName) {
            root = getConfiguration(rootModule, rootConfigurationName);
        }

        ModuleRevisionResolveState getRevision(ModuleDescriptor descriptor) {
            ModuleRevisionId original = descriptor.getModuleRevisionId();
            ModuleRevisionId id = new ModuleRevisionId(new ModuleId(original.getOrganisation(), original.getName()), original.getRevision());
            ModuleRevisionResolveState moduleRevision = revisions.get(id);
            if (moduleRevision == null) {
                moduleRevision = new ModuleRevisionResolveState(id, descriptor);
                revisions.put(id, moduleRevision);
                ModuleId moduleId = id.getModuleId();
                modules.put(moduleId, moduleRevision);
                Set<ModuleRevisionResolveState> revisionsForModule = modules.get(moduleId);
                if (revisionsForModule.size() > 1) {
                    for (ModuleRevisionResolveState revision : revisionsForModule) {
                        revision.status = Status.Conflict;
                    }
                }
            }

            return moduleRevision;
        }

        ConfigurationResolveState getConfiguration(ModuleDescriptor descriptor, String configurationName) {
            ModuleRevisionId original = descriptor.getModuleRevisionId();
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(original.getOrganisation(), original.getName(), original.getRevision(), configurationName);
            ConfigurationResolveState configuration = configurations.get(id);
            if (configuration == null) {
                ModuleRevisionResolveState moduleRevision = getRevision(descriptor);
                configuration = new ConfigurationResolveState(moduleRevision, descriptor, configurationName, this);
                configurations.put(id, configuration);
            }
            return configuration;
        }

        public Collection<ConfigurationResolveState> getConfigurations() {
            return configurations.values();
        }

        public Set<ModuleRevisionResolveState> getRevisions(ModuleId moduleId) {
            return modules.get(moduleId);
        }
    }

    enum Status {Include, Conflict, Evict}

    private static class ModuleRevisionResolveState implements ModuleRevisionState {
        final ModuleRevisionId id;
        final ModuleDescriptor descriptor;
        Status status = Status.Include;
        final Set<DependencyResolvePath> incomingPaths = new LinkedHashSet<DependencyResolvePath>();
        Set<DependencyResolveState> dependencies;

        private ModuleRevisionResolveState(ModuleRevisionId id, ModuleDescriptor descriptor) {
            this.id = id;
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return id.toString();
        }

        public String getRevision() {
            return descriptor.getRevision();
        }

        public Status getStatus() {
            return status;
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
        final ModuleRevisionResolveState moduleRevision;
        final ModuleDescriptor descriptor;
        final String configurationName;
        final Set<String> heirarchy = new LinkedHashSet<String>();
        final Set<ResolvePath> incomingPaths = new LinkedHashSet<ResolvePath>();
        DefaultResolvedDependency result;
        Set<ResolvedArtifact> artifacts;

        private ConfigurationResolveState(ModuleRevisionResolveState moduleRevision, ModuleDescriptor descriptor, String configurationName, ResolveState container) {
            this.moduleRevision = moduleRevision;
            this.descriptor = descriptor;
            this.configurationName = configurationName;
            findAncestors(configurationName, container, heirarchy);
        }

        Status getStatus() {
            return moduleRevision.getStatus();
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

        void addOutgoingDependencies(ResolvePath incomingPath, Collection<DependencyResolvePath> dependencies) {
            if (incomingPath.canReach(this)) {
                LOGGER.debug("Skipping {} as it already traverses {}.", incomingPath, this);
                return;
            }
            for (DependencyResolveState dependency : moduleRevision.getDependencies()) {
                Set<String> targetConfigurations = dependency.getTargetConfigurations(this);
                if (!targetConfigurations.isEmpty()) {
                    DependencyResolvePath dependencyResolvePath = new DependencyResolvePath(incomingPath, this, dependency, targetConfigurations);
                    dependencies.add(dependencyResolvePath);
                }
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

        public void attachToParents(ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result) {
            switch (getStatus()) {
                case Include:
                    LOGGER.debug("Attaching {} to its parents.", this);
                    for (ResolvePath incomingPath : incomingPaths) {
                        incomingPath.attachToParents(this, resolvedArtifactFactory, resolver, result);
                    }
                    break;
                case Evict:
                    LOGGER.debug("Ignoring evicted {}.", this);
                    break;
                default:
                    throw new IllegalStateException(String.format("Unexpected state %s for %s at end of resolution.", getStatus(), this));
            }
        }

        public boolean isTransitive() {
            return descriptor.getConfiguration(configurationName).isTransitive();
        }
    }

    private static abstract class ResolvePath {
        public abstract void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result);

        public abstract boolean excludes(ModuleRevisionResolveState moduleRevision);

        public abstract boolean canReach(ConfigurationResolveState configuration);

        public abstract void addPathAsModules(Collection<ModuleRevisionResolveState> modules);
    }

    private static class RootPath extends ResolvePath {
        @Override
        public String toString() {
            return "<root>";
        }

        @Override
        public boolean excludes(ModuleRevisionResolveState moduleRevision) {
            return false;
        }

        @Override
        public boolean canReach(ConfigurationResolveState configuration) {
            return false;
        }

        @Override
        public void addPathAsModules(Collection<ModuleRevisionResolveState> modules) {
        }

        @Override
        public void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationBuilder result) {
            // Don't need to do anything
        }
    }

    private static class DependencyResolveState {
        final DependencyDescriptor descriptor;
        ModuleRevisionResolveState targetModuleRevision;
        ResolvedModuleRevision resolvedRevision;

        private DependencyResolveState(DependencyDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return descriptor.toString();
        }

        public void resolve(DependencyToModuleResolver resolver, ResolveState resolveState) {
            if (resolvedRevision == null) {
                resolvedRevision = resolver.resolve(descriptor);
                targetModuleRevision = resolveState.getRevision(resolvedRevision.getDescriptor());
            }
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
        ModuleRevisionResolveState targetModuleRevision;

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

        public void resolve(DependencyToModuleResolver resolver, ResolveState resolveState) {
            if (targetModuleRevision == null) {
                try {
                    dependency.resolve(resolver, resolveState);
                } catch (ModuleNotFoundException e) {
                    Formatter formatter = new Formatter();
                    formatter.format("Module %s not found. It is required by:", dependency.getDependencyId());
                    Set<ModuleRevisionResolveState> modules = new LinkedHashSet<ModuleRevisionResolveState>();
                    addPathAsModules(modules);
                    for (ModuleRevisionResolveState module : modules) {
                        formatter.format("%n    %s", module.getId());
                    }
                    throw new ModuleNotFoundException(formatter.toString(), e);
                }

                referTo(dependency.targetModuleRevision);
            } // Else, we've been restarted
        }

        private void referTo(ModuleRevisionResolveState targetModuleRevision) {
            this.targetModuleRevision = targetModuleRevision;
            if (!excludes(targetModuleRevision)) {
                targetModuleRevision.addIncomingPath(this);
            }
        }

        public void addOutgoingDependencies(ResolveData resolveData, ResolveState resolveState, Collection<DependencyResolvePath> queue) {
            if (excludes(targetModuleRevision)) {
                return;
            }

            ModuleDescriptor targetDescriptor = targetModuleRevision.descriptor;

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
        public boolean excludes(ModuleRevisionResolveState moduleRevision) {
            String[] configurations = from.heirarchy.toArray(new String[from.heirarchy.size()]);
            ArtifactId placeholderArtifact = new ArtifactId(moduleRevision.id.getModuleId(), "ivy", "ivy", "ivy");
            boolean excluded = dependency.descriptor.doesExclude(configurations, placeholderArtifact);
            if (excluded) {
                LOGGER.debug("{} is excluded by {}.", moduleRevision, this);
                return true;
            }
            excluded = from.descriptor.doesExclude(configurations, placeholderArtifact);
            if (excluded) {
                LOGGER.debug("{} is excluded by {}.", moduleRevision, from);
                return true;
            }
            return path.excludes(moduleRevision);
        }

        @Override
        public void addPathAsModules(Collection<ModuleRevisionResolveState> modules) {
            modules.add(from.moduleRevision);
            path.addPathAsModules(modules);
        }

        @Override
        public boolean canReach(ConfigurationResolveState configuration) {
            return from.equals(configuration) || path.canReach(configuration);
        }

        public void restart(ModuleRevisionResolveState moduleRevision, List<DependencyResolvePath> queue) {
            assert targetModuleRevision != null;
            targetModuleRevision.incomingPaths.remove(this);
            referTo(moduleRevision);
            LOGGER.debug("Restarting {} on conflict, now refers to {}.", this, moduleRevision);
            queue.add(this);
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

        ModuleRevisionResolveState select(Collection<ModuleRevisionResolveState> candidates, ModuleRevisionResolveState root) {
            for (ModuleRevisionResolveState candidate : candidates) {
                for (DependencyResolvePath incomingPath : candidate.incomingPaths) {
                    if (incomingPath.from.moduleRevision == root && incomingPath.dependency.descriptor.isForce()) {
                        return candidate;
                    }
                }
            }
            return (ModuleRevisionResolveState) resolver.select(candidates, root);
        }
    }
}
