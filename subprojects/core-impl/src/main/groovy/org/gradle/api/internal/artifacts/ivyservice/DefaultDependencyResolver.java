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

import org.apache.commons.lang.StringUtils;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.IvyContext;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.resolve.IvyNode;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
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
        resolve(ivy, resolver, result, moduleDescriptor, configuration);
        return result;
    }

    private void resolve(Ivy ivy, DependencyResolver resolver, ResolvedConfigurationImpl result, ModuleDescriptor moduleDescriptor, Configuration configuration) {
        System.out.println("-> RESOLVE " + configuration);

        List<Throwable> failures = new ArrayList<Throwable>();
        ConfigurationContainer container = new ConfigurationContainer();
        ConfigurationResolveState root = container.getConfiguration(moduleDescriptor, configuration.getName());

        List<DependencyResolveState> queue = new ArrayList<DependencyResolveState>();
        root.addOutgoingDependencies(queue);

        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configuration.getName()));
        ResolveData resolveData = new ResolveData(ivy.getResolveEngine(), options);

        while (!queue.isEmpty()) {
            DependencyResolveState dependency = queue.remove(0);
            System.out.println("resolving " + dependency);
            IvyContext context = IvyContext.pushNewCopyContext();
            ResolvedModuleRevision resolvedRevision;
            try {
                context.setIvy(ivy);
                context.setResolveData(resolveData);
                context.setDependencyDescriptor(dependency.descriptor);
                try {
                    resolvedRevision = resolver.getDependency(dependency.descriptor, resolveData);
                    if (resolvedRevision == null) {
                        throw new RuntimeException(String.format("%s not found.", StringUtils.capitalize(dependency.toString())));
                    }
                } catch (Throwable t) {
                    failures.add(t);
                    continue;
                }
            } finally {
                IvyContext.popContext();
            }

            System.out.println("  found module " + resolvedRevision.getId());
            for (String targetConfigurationName : dependency.getTargetConfigurations(resolvedRevision.getDescriptor(), resolveData)) {
                ConfigurationResolveState targetConfiguration = container.getConfiguration(resolvedRevision.getDescriptor(), targetConfigurationName);
                DefaultResolvedDependency owner = targetConfiguration.getResult(); 
                for (Artifact artifact : targetConfiguration.getArtifacts()) {
                    System.out.println("      added artifact " + artifact);
                    result.addArtifact(resolvedArtifactFactory.create(owner, artifact, resolvedRevision.getArtifactResolver()));
                }
                System.out.println("    refers to config " + targetConfiguration);
                targetConfiguration.addOutgoingDependencies(queue);
            }
        }

        if (!failures.isEmpty()) {
            throw new ResolveException(configuration, Collections.<String>emptyList(), failures);
        }


    }


    private static class ConfigurationContainer {
        final Map<ResolvedConfigurationIdentifier, ConfigurationResolveState> configurations = new HashMap<ResolvedConfigurationIdentifier, ConfigurationResolveState>();
        
        ConfigurationResolveState getConfiguration(ModuleDescriptor descriptor, String configurationName) {
            ResolvedConfigurationIdentifier id = new ResolvedConfigurationIdentifier(descriptor.getModuleRevisionId(), configurationName);
            ConfigurationResolveState configuration = configurations.get(id);
            if (configuration == null) {
                configuration = new ConfigurationResolveState(descriptor, configurationName, this);
                configurations.put(id, configuration);
            }
            return configuration;
        }
        
    }

    private static class ConfigurationResolveState {
        final ModuleDescriptor descriptor;
        final String configurationName;
        final Set<String> heirarchy = new LinkedHashSet<String>();
        DefaultResolvedDependency result;

        private ConfigurationResolveState(ModuleDescriptor descriptor, String configurationName, ConfigurationContainer container) {
            this.descriptor = descriptor;
            this.configurationName = configurationName;
            findAncestors(configurationName, container, heirarchy);
        }

        void findAncestors(String config, ConfigurationContainer container, Set<String> ancestors) {
            ancestors.add(config);
            for (String parentConfig : descriptor.getConfiguration(config).getExtends()) {
                ancestors.addAll(container.getConfiguration(descriptor, parentConfig).heirarchy);
            }
        }
        
        void addOutgoingDependencies(Collection<DependencyResolveState> dependencies) {
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
                    DependencyResolveState dependencyResolveState = new DependencyResolveState(this, dependencyDescriptor, targetConfigurations);
                    System.out.println("      adding " + dependencyResolveState);
                    dependencies.add(dependencyResolveState);
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
    }

    private static class DependencyResolveState {
        final ConfigurationResolveState from;
        final DependencyDescriptor descriptor;
        final Set<String> targetConfigurations;

        private DependencyResolveState(ConfigurationResolveState from, DependencyDescriptor descriptor, Set<String> targetConfigurations) {
            this.from = from;
            this.descriptor = descriptor;
            this.targetConfigurations = targetConfigurations;
        }

        @Override
        public String toString() {
            return String.format("%s -> %s(%s)", from, descriptor.getDependencyRevisionId(), targetConfigurations);
        }

        public Set<String> getTargetConfigurations(ModuleDescriptor moduleDescriptor, ResolveData resolveData) {
            IvyNode node = new IvyNode(resolveData, moduleDescriptor);
            Set<String> targets = new LinkedHashSet<String>();
            for (String targetConfiguration : targetConfigurations) {
                for (String realConfig : node.getRealConfs(targetConfiguration)) {
                    targets.add(realConfig);
                }
            }
            return targets;
        }
    }

    private static class ResolvedConfigurationImpl implements ResolvedConfiguration {
        private final Set<ResolvedArtifact> artifacts = new LinkedHashSet<ResolvedArtifact>(); 
        
        public boolean hasError() {
            return false;
        }

        public LenientConfiguration getLenientConfiguration() {
            throw new UnsupportedOperationException();
        }

        public void rethrowFailure() throws ResolveException {
        }

        public Set<File> getFiles(Spec<? super Dependency> dependencySpec) throws ResolveException {
            System.out.println("--> FILES");
            Set<File> files = new LinkedHashSet<File>();
            for (ResolvedArtifact artifact : artifacts) {
                System.out.println("  " + artifact.getFile());
                files.add(artifact.getFile());
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

        public void addArtifact(ResolvedArtifact resolvedArtifact) {
            artifacts.add(resolvedArtifact);
        }
    }
}
