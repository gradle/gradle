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
package org.gradle.api.internal.artifacts.ivyservice;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.*;
import org.apache.ivy.core.module.id.ArtifactId;
import org.apache.ivy.core.module.id.ModuleId;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.latest.ArtifactInfo;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.IvyConfig;
import org.gradle.api.specs.Spec;
import org.gradle.util.WrapUtil;

import java.util.*;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final ResolveIvyFactory ivyFactory;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory) {
        this.ivyFactory = ivyFactory;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) throws ResolveException {
        Ivy ivy = ivyFactory.create(configuration.getResolutionStrategy());

        IvyConfig ivyConfig = new IvyConfig(ivy.getSettings(), configuration.getResolutionStrategy());
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configuration.getAll(), configuration.getModule(), ivyConfig);
        DependencyResolver resolver = ivy.getSettings().getDefaultResolver();
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configuration.getName()));
        ResolveData resolveData = new ResolveData(ivy.getResolveEngine(), options);
        DependencyToModuleResolver dependencyResolver = new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, resolver);
        IvyResolverBackedArtifactToFileResolver artifactResolver = new IvyResolverBackedArtifactToFileResolver(resolver);
        ResolveState resolveState = new ResolveState();
        ConfigurationResolveState root = resolveState.getConfiguration(moduleDescriptor, configuration.getName());
        ResolvedConfigurationImpl result = new ResolvedConfigurationImpl(root.getResult());
        resolve(dependencyResolver, result, root, resolveState, resolveData, artifactResolver, configuration);
        return result;
    }

    private void resolve(DependencyToModuleResolver resolver, ResolvedConfigurationImpl result, ConfigurationResolveState root, ResolveState resolveState, ResolveData resolveData, ArtifactToFileResolver artifactResolver, Configuration configuration) {
        System.out.println("-> RESOLVE " + root);

        List<Throwable> failures = new ArrayList<Throwable>();
        SetMultimap<ModuleId, DependencyResolvePath> conflicts = LinkedHashMultimap.create();

        List<DependencyResolvePath> queue = new ArrayList<DependencyResolvePath>();
        root.addOutgoingDependencies(new RootPath(), queue);

        while (!queue.isEmpty() || !conflicts.isEmpty()) {
            if (queue.isEmpty()) {
                ModuleId moduleId = conflicts.keySet().iterator().next();
                Set<ModuleRevisionResolveState> candidates = resolveState.getRevisions(moduleId);
                System.out.println("selecting moduleId from conflicts " + candidates);
                List<ModuleResolveStateBackedArtifactInfo> artifactInfos = new ArrayList<ModuleResolveStateBackedArtifactInfo>();
                for (final ModuleRevisionResolveState moduleRevision : candidates) {
                    artifactInfos.add(new ModuleResolveStateBackedArtifactInfo(moduleRevision));
                }
                List<ModuleResolveStateBackedArtifactInfo> sorted = new LatestRevisionStrategy().sort(artifactInfos.toArray(new ArtifactInfo[artifactInfos.size()]));
                ModuleRevisionResolveState selected = sorted.get(sorted.size() - 1).moduleRevision;
                System.out.println("  selected " + selected);
                selected.status = Status.Include;
                for (ModuleRevisionResolveState candidate : candidates) {
                    if (candidate != selected) {
                        candidate.status = Status.Evict;
                        for (DependencyResolvePath path : candidate.outgoingPaths) {
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
            System.out.println("* path " + path);

            try {
                path.resolve(resolver, resolveState);
            } catch (Throwable t) {
                failures.add(t);
                continue;
            }

            if (path.targetModuleRevision.status == Status.Conflict) {
                conflicts.put(path.resolvedRevision.getId().getModuleId(), path);
            } else {
                path.addOutgoingDependencies(resolveData, resolveState, queue);
            }
        }
        
        if (!failures.isEmpty()) {
            throw new ResolveException(configuration, Collections.<String>emptyList(), failures);
        }

        for (ConfigurationResolveState resolvedConfiguration : resolveState.getConfigurations()) {
            resolvedConfiguration.attachToParents(resolvedArtifactFactory, artifactResolver, result);
        }
    }

    private static class ResolveState {
        final SetMultimap<ModuleId, ModuleRevisionResolveState> modules = LinkedHashMultimap.create();
        final Map<ModuleRevisionId, ModuleRevisionResolveState> revisions = new LinkedHashMap<ModuleRevisionId, ModuleRevisionResolveState>();
        final Map<ResolvedConfigurationIdentifier, ConfigurationResolveState> configurations = new LinkedHashMap<ResolvedConfigurationIdentifier, ConfigurationResolveState>();

        ModuleRevisionResolveState getRevision(ModuleDescriptor descriptor) {
            ModuleRevisionResolveState moduleRevision = revisions.get(descriptor.getModuleRevisionId());
            if (moduleRevision == null) {
                moduleRevision = new ModuleRevisionResolveState(descriptor);
                revisions.put(descriptor.getModuleRevisionId(), moduleRevision);
                ModuleId moduleId = descriptor.getModuleRevisionId().getModuleId();
                modules.put(moduleId, moduleRevision);
                Set<ModuleRevisionResolveState> revisionsForModule = modules.get(moduleId);
                if (revisionsForModule.size() > 1) {
                    System.out.println("-> conflicts " + revisionsForModule);
                    for (ModuleRevisionResolveState revision : revisionsForModule) {
                        revision.status = Status.Conflict;
                    }
                }
            }
            
            return moduleRevision;
        }

        ConfigurationResolveState getConfiguration(ModuleDescriptor descriptor, String configurationName) {
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(descriptor.getModuleRevisionId(), configurationName);
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

    enum Status { Include, Conflict, Evict }

    private static class ModuleRevisionResolveState {
        final ModuleDescriptor descriptor;
        Status status = Status.Include;
        final Set<DependencyResolvePath> outgoingPaths = new LinkedHashSet<DependencyResolvePath>();

        private ModuleRevisionResolveState(ModuleDescriptor descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        public String toString() {
            return descriptor.getModuleRevisionId().toString();
        }

        public Status getStatus() {
            return status;
        }

        public void addOutgoingPath(DependencyResolvePath path) {
            outgoingPaths.add(path);
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

        void addOutgoingDependencies(ResolvePath incomingPath, Collection<DependencyResolvePath> dependencies) {
            incomingPaths.add(incomingPath);
            for (DependencyDescriptor dependencyDescriptor : descriptor.getDependencies()) {
                Set<String> targetConfigurations = new LinkedHashSet<String>();
                for (String moduleConfiguration : dependencyDescriptor.getModuleConfigurations()) {
                    if (heirarchy.contains(moduleConfiguration)) {
                        for (String targetConfiguration : dependencyDescriptor.getDependencyConfigurations(moduleConfiguration)) {
                            targetConfigurations.add(targetConfiguration);
                        }
                    }
                }
                if (!targetConfigurations.isEmpty()) {
                    DependencyResolvePath dependencyResolvePath = new DependencyResolvePath(incomingPath, this, dependencyDescriptor, targetConfigurations);
                    dependencies.add(dependencyResolvePath);
                }
            }
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", descriptor.getModuleRevisionId(), configurationName);
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
                        descriptor.getModuleRevisionId().getOrganisation(),
                        descriptor.getModuleRevisionId().getName(),
                        descriptor.getModuleRevisionId().getRevision(),
                        configurationName);
            }

            return result;
        }

        public void attachToParents(ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationImpl result) {
            switch (getStatus()) {
                case Include:
                    System.out.println("Attaching " + this + " to parents");
                    for (ResolvePath incomingPath : incomingPaths) {
                        incomingPath.attachToParents(this, resolvedArtifactFactory, resolver, result);
                    }
                    break;
                case Evict:
                    System.out.println("Ignoring evicted configuration " + this);
                    break;
                default:
                    throw new IllegalStateException(String.format("Unexpected state %s for %s at end of resolution.", getStatus(), this));
            }
        }
    }

    private static abstract class ResolvePath {
        public abstract void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationImpl result);

        public abstract boolean isExcluded(ModuleRevisionResolveState moduleRevision);
    }
    
    private static class RootPath extends ResolvePath {
        @Override
        public String toString() {
            return "<root>";
        }

        @Override
        public boolean isExcluded(ModuleRevisionResolveState moduleRevision) {
            return false;
        }

        @Override
        public void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationImpl result) {
            // Don't need to do anything
        }
    }

    private static class DependencyResolvePath extends ResolvePath {
        final ResolvePath path;
        final ConfigurationResolveState from;
        final DependencyDescriptor descriptor;
        final Set<String> targetConfigurations;
        ModuleRevisionResolveState targetModuleRevision;
        ResolvedModuleRevision resolvedRevision;

        private DependencyResolvePath(ResolvePath path, ConfigurationResolveState from, DependencyDescriptor descriptor, Set<String> targetConfigurations) {
            this.path = path;
            this.from = from;
            this.descriptor = descriptor;
            this.targetConfigurations = targetConfigurations;
        }

        @Override
        public String toString() {
            return String.format("%s | %s -> %s(%s)", path, from, descriptor.getDependencyRevisionId(), targetConfigurations);
        }

        public Set<String> getTargetConfigurations(ModuleDescriptor targetModule, ResolveData resolveData) {
            IvyNode node = new IvyNode(resolveData, targetModule);
            Set<String> targets = new LinkedHashSet<String>();
            for (String targetConfiguration : targetConfigurations) {
                for (String realConfig : node.getRealConfs(targetConfiguration)) {
                    targets.add(realConfig);
                }
            }
            return targets;
        }

        public void resolve(DependencyToModuleResolver resolver, ResolveState resolveState) {
            if (resolvedRevision == null) {
                resolvedRevision = resolver.resolve(descriptor);
                targetModuleRevision = resolveState.getRevision(resolvedRevision.getDescriptor());
                System.out.println("  resolved to " + targetModuleRevision);
            }
        }

        public void addOutgoingDependencies(ResolveData resolveData, ResolveState resolveState, Collection<DependencyResolvePath> queue) {
            if (isExcluded(targetModuleRevision)) {
                return;
            }

            targetModuleRevision.addOutgoingPath(this);
            ModuleDescriptor targetDescriptor = targetModuleRevision.descriptor;
            for (String targetConfigurationName : getTargetConfigurations(targetDescriptor, resolveData)) {
                ConfigurationResolveState targetConfiguration = resolveState.getConfiguration(targetDescriptor, targetConfigurationName);
                System.out.println("    refers to config " + targetConfiguration);
                targetConfiguration.addOutgoingDependencies(this, queue);
            }
        }

        public boolean isExcluded(ModuleRevisionResolveState moduleRevision) {
            boolean excluded = descriptor.doesExclude(new String[]{from.configurationName}, new ArtifactId(moduleRevision.descriptor.getModuleRevisionId().getModuleId(), "ivy", "ivy", "ivy"));
            if (excluded) {
                System.out.println("   excluded by " + this);
                return true;
            }
            return path.isExcluded(moduleRevision);
        }

        public void restart(ModuleRevisionResolveState moduleRevision, List<DependencyResolvePath> queue) {
            assert targetModuleRevision != null;
            targetModuleRevision = moduleRevision;
            System.out.println("    restart " + this + " with " + moduleRevision);
            queue.add(this);
        }

        private Set<ResolvedArtifact> getArtifacts(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver) {
            DependencyArtifactDescriptor[] dependencyArtifacts = descriptor.getDependencyArtifacts(from.configurationName);
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
        public void attachToParents(ConfigurationResolveState childConfiguration, ResolvedArtifactFactory resolvedArtifactFactory, ArtifactToFileResolver resolver, ResolvedConfigurationImpl result) {
            System.out.println("  attach via " + this);
            System.out.println("    " + from + " -> " + childConfiguration);
            DefaultResolvedDependency parent = from.getResult();
            DefaultResolvedDependency child = childConfiguration.getResult();
            parent.addChild(child);

            Set<ResolvedArtifact> artifacts = getArtifacts(childConfiguration, resolvedArtifactFactory, resolver);
            if (artifacts.isEmpty()) {
                artifacts = childConfiguration.getArtifacts(resolvedArtifactFactory, resolver);
            }
            child.addParentSpecificArtifacts(parent, artifacts);
            for (ResolvedArtifact artifact : artifacts) {
                result.addArtifact(artifact);
            }

            if (parent == result.getRoot()) {
                EnhancedDependencyDescriptor enhancedDependencyDescriptor = (EnhancedDependencyDescriptor) descriptor;
                result.addFirstLevelDependency(enhancedDependencyDescriptor.getModuleDependency(), child);
            }
        }
    }

    private static class ResolvedConfigurationImpl extends AbstractResolvedConfiguration {
        private final ResolvedDependency root;
        private final Map<ModuleDependency, ResolvedDependency> firstLevelDependencies = new LinkedHashMap<ModuleDependency, ResolvedDependency>();
        private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>();

        private ResolvedConfigurationImpl(ResolvedDependency root) {
            this.root = root;
        }

        public boolean hasError() {
            return false;
        }

        public void rethrowFailure() throws ResolveException {
        }

        public LenientConfiguration getLenientConfiguration() {
            throw new UnsupportedOperationException();
        }

        @Override
        Set<ResolvedDependency> doGetFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) {
            Set<ResolvedDependency> matches = new LinkedHashSet<ResolvedDependency>();
            for (Map.Entry<ModuleDependency, ResolvedDependency> entry : firstLevelDependencies.entrySet()) {
                if (dependencySpec.isSatisfiedBy(entry.getKey())) {
                    matches.add(entry.getValue());
                }
            }
            return matches;
        }

        @Override
        protected ResolvedDependency getRoot() {
            return root;
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            return artifacts;
        }

        public void addFirstLevelDependency(ModuleDependency moduleDependency, ResolvedDependency refersTo) {
            firstLevelDependencies.put(moduleDependency, refersTo);
        }

        public void addArtifact(ResolvedArtifact artifact) {
            artifacts.add(artifact);
        }
    }

    private static class ModuleResolveStateBackedArtifactInfo implements ArtifactInfo {
        final ModuleRevisionResolveState moduleRevision;

        public ModuleResolveStateBackedArtifactInfo(ModuleRevisionResolveState moduleRevision) {
            this.moduleRevision = moduleRevision;
        }

        public String getRevision() {
            return moduleRevision.descriptor.getRevision();
        }

        public long getLastModified() {
            throw new UnsupportedOperationException();
        }
    }
}
