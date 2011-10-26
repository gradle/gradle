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
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
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
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DefaultResolvedDependency;
import org.gradle.api.internal.artifacts.ResolvedConfigurationIdentifier;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.IvyConfig;
import org.gradle.api.specs.Spec;
import org.gradle.util.WrapUtil;

import java.io.File;
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
        ResolvedConfigurationImpl result = new ResolvedConfigurationImpl();
        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configuration.getName()));
        ResolveData resolveData = new ResolveData(ivy.getResolveEngine(), options);
        DependencyToModuleResolver dependencyResolver = new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, resolver);
        resolve(dependencyResolver, result, moduleDescriptor, configuration, resolveData, resolver);
        return result;
    }

    private void resolve(DependencyToModuleResolver resolver, ResolvedConfigurationImpl result, ModuleDescriptor moduleDescriptor, Configuration configuration, ResolveData resolveData, DependencyResolver dependencyResolver) {
        System.out.println("-> RESOLVE " + configuration);

        List<Throwable> failures = new ArrayList<Throwable>();
        ResolveState resolveState = new ResolveState();
        SetMultimap<ModuleId, DependencyResolvePath> conflicts = LinkedHashMultimap.create();
        ConfigurationResolveState root = resolveState.getConfiguration(moduleDescriptor, configuration.getName());

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
                        for (DependencyResolvePath path : candidate.paths) {
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
            if (resolvedConfiguration.getStatus() == Status.Include) {
                resolvedConfiguration.addArtifacts(resolvedArtifactFactory, dependencyResolver);
                result.add(resolvedConfiguration.getResult());
            } else {
                assert resolvedConfiguration.getStatus() == Status.Evict;
                System.out.println("Ignoring evicted configuration " + resolvedConfiguration);
            }
        }
    }

    private interface DependencyToModuleResolver {
        ResolvedModuleRevision resolve(DependencyDescriptor dependencyDescriptor);
    }

    private static class IvyResolverBackedDependencyToModuleResolver implements DependencyToModuleResolver {
        private final Ivy ivy;
        private final ResolveData resolveData;
        private final DependencyResolver resolver;

        private IvyResolverBackedDependencyToModuleResolver(Ivy ivy, ResolveData resolveData, DependencyResolver resolver) {
            this.ivy = ivy;
            this.resolveData = resolveData;
            this.resolver = resolver;
        }

        public ResolvedModuleRevision resolve(DependencyDescriptor dependencyDescriptor) {
            IvyContext context = IvyContext.pushNewCopyContext();
            try {
                context.setIvy(ivy);
                context.setResolveData(resolveData);
                context.setDependencyDescriptor(dependencyDescriptor);
                ResolvedModuleRevision resolvedRevision = null;
                try {
                    resolvedRevision = resolver.getDependency(dependencyDescriptor, resolveData);
                } catch (Throwable t) {
                    throw new RuntimeException(String.format("Could not resolve %s", dependencyDescriptor.getDependencyRevisionId()), t);
                }
                if (resolvedRevision == null) {
                    throw new RuntimeException(String.format("%s not found.", dependencyDescriptor.getDependencyRevisionId()));
                }
                return resolvedRevision;
            } finally {
                IvyContext.popContext();
            }
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
        final Set<DependencyResolvePath> paths = new LinkedHashSet<DependencyResolvePath>();

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

        public void addPath(DependencyResolvePath path) {
            paths.add(path);
        }
    }

    private static class ConfigurationResolveState {
        final ModuleRevisionResolveState moduleRevision;
        final ModuleDescriptor descriptor;
        final String configurationName;
        final Set<String> heirarchy = new LinkedHashSet<String>();
        DefaultResolvedDependency result;

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

        void addOutgoingDependencies(ResolvePath path, Collection<DependencyResolvePath> dependencies) {
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
                    DependencyResolvePath dependencyResolvePath = new DependencyResolvePath(path, this, dependencyDescriptor, targetConfigurations);
                    dependencies.add(dependencyResolvePath);
                }
            }
        }

        @Override
        public String toString() {
            return String.format("%s(%s)", descriptor.getModuleRevisionId(), configurationName);
        }

        public Set<Artifact> getArtifacts() {
            Set<Artifact> artifacts = new LinkedHashSet<Artifact>();
            for (String config : heirarchy) {
                for (Artifact artifact : descriptor.getArtifacts(config)) {
                    artifacts.add(artifact);
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

        public void addArtifacts(ResolvedArtifactFactory resolvedArtifactFactory, DependencyResolver resolver) {
            DefaultResolvedDependency owner = getResult();
            for (Artifact artifact : getArtifacts()) {
                owner.addModuleArtifact(resolvedArtifactFactory.create(owner, artifact, resolver));
            }
        }
    }

    private static abstract class ResolvePath {
        
    }
    
    private static class RootPath extends ResolvePath {
        @Override
        public String toString() {
            return "<root>";
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
            targetModuleRevision.addPath(this);
            ModuleDescriptor targetDescrptor = targetModuleRevision.descriptor;
            for (String targetConfigurationName : getTargetConfigurations(targetDescrptor, resolveData)) {
                ConfigurationResolveState targetConfiguration = resolveState.getConfiguration(targetDescrptor, targetConfigurationName);
                System.out.println("    refers to config " + targetConfiguration);
                targetConfiguration.addOutgoingDependencies(this, queue);
            }
        }

        public void restart(ModuleRevisionResolveState moduleRevision, List<DependencyResolvePath> queue) {
            assert targetModuleRevision != null;
            targetModuleRevision = moduleRevision;
            System.out.println("    restart " + this + " with " + moduleRevision);
            queue.add(this);
        }
    }

    private static class ResolvedConfigurationImpl implements ResolvedConfiguration {
        private final Set<ResolvedDependency> dependencies = new LinkedHashSet<ResolvedDependency>();

        public boolean hasError() {
            return false;
        }

        public LenientConfiguration getLenientConfiguration() {
            throw new UnsupportedOperationException();
        }

        public void rethrowFailure() throws ResolveException {
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            Set<File> files = new LinkedHashSet<File>();
            for (ResolvedDependency dependency : dependencies) {
                for (ResolvedArtifact artifact : dependency.getModuleArtifacts()) {
                    files.add(artifact.getFile());
                }
            }
            System.out.println("--> FILES");
            for (File file : files) {
                System.out.println("  " + file);
            }
            return files;
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies() throws ResolveException {
            throw new UnsupportedOperationException();
        }

        public Set<ResolvedDependency> getFirstLevelModuleDependencies(Spec<? super Dependency> dependencySpec) throws ResolveException {
            throw new UnsupportedOperationException();
        }

        public Set<ResolvedArtifact> getResolvedArtifacts() throws ResolveException {
            throw new UnsupportedOperationException();
        }

        public void add(DefaultResolvedDependency dependency) {
            dependencies.add(dependency);
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
