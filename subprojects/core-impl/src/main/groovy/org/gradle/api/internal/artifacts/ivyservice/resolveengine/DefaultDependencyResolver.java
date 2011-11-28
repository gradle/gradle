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

import org.apache.ivy.core.resolve.ResolveData;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.conflicts.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyAdapter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final ResolveIvyFactory ivyFactory;
    private final ClientModuleRegistry clientModuleRegistry;
    private final ProjectModuleRegistry projectModuleRegistry;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory,
                                     ProjectModuleRegistry projectModuleRegistry, ClientModuleRegistry clientModuleRegistry) {
        this.ivyFactory = ivyFactory;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.clientModuleRegistry = clientModuleRegistry;
        this.projectModuleRegistry = projectModuleRegistry;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) throws ResolveException {
        LOGGER.debug("Resolving {}", configuration);

        IvyAdapter ivyAdapter = ivyFactory.create(configuration.getResolutionStrategy());
        ResolveData resolveData = ivyAdapter.getResolveData(configuration.getName());

        DependencyToModuleResolver dependencyResolver = constructDependencyResolver(configuration, ivyAdapter.getDependencyToModuleResolver(resolveData));
        ArtifactToFileResolver artifactResolver = constructArtifactResolver(ivyAdapter.getArtifactToFileResolver());

        ModuleConflictResolver conflictResolver;
        if (configuration.getResolutionStrategy().getConflictResolution() instanceof StrictConflictResolution) {
            conflictResolver = new StrictConflictResolver();
        } else {
            conflictResolver = new LatestModuleConflictResolver();
        }

        DependencyGraphBuilder builder = new DependencyGraphBuilder(moduleDescriptorConverter, resolvedArtifactFactory, artifactResolver, dependencyResolver, conflictResolver);
        DefaultLenientConfiguration result = builder.resolve(configuration, resolveData);
        return new DefaultResolvedConfiguration(result);
    }

    private DependencyToModuleResolver constructDependencyResolver(ConfigurationInternal configuration, DependencyToModuleResolver ivyBackedResolver) {
        DependencyToModuleResolver clientModuleResolver = new ClientModuleResolver(clientModuleRegistry);
        DependencyToModuleResolver projectModuleResolver = new ProjectDependencyResolver(projectModuleRegistry);
        DependencyToModuleResolver dependencyToModuleResolverChain = new DependencyToModuleResolverChain(clientModuleResolver, projectModuleResolver, ivyBackedResolver);
        return new VersionForcingDependencyToModuleResolver(dependencyToModuleResolverChain, configuration.getResolutionStrategy().getForcedModules());
    }

    private ArtifactToFileResolver constructArtifactResolver(ArtifactToFileResolver ivyBackedArtifactResolver) {
        ArtifactToFileResolver projectArtifactResolver = new ProjectDependencyResolver(projectModuleRegistry);
        return new ArtifactToFileResolverChain(projectArtifactResolver, ivyBackedArtifactResolver);
    }
}
