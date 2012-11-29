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

import org.gradle.api.Action;
import org.gradle.api.artifacts.ForcedModuleDetails;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.ResolverResults;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.ModuleForcingStrategy;
import org.gradle.api.internal.artifacts.configurations.conflicts.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.clientmodule.ClientModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.forcing.ForcedModuleRulesFactory;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.IvyAdapter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LazyDependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ResolveIvyFactory;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectDependencyResolver;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectModuleRegistry;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ResolutionResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final ResolveIvyFactory ivyFactory;
    private final ProjectModuleRegistry projectModuleRegistry;
    private final ForcedModuleRulesFactory forcedModuleRulesFactory = new ForcedModuleRulesFactory();

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory,
                                     ProjectModuleRegistry projectModuleRegistry) {
        this.ivyFactory = ivyFactory;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
        this.projectModuleRegistry = projectModuleRegistry;
    }

    public ResolverResults resolve(ConfigurationInternal configuration) throws ResolveException {
        LOGGER.debug("Resolving {}", configuration);

        IvyAdapter ivyAdapter = ivyFactory.create(configuration);

        DependencyToModuleResolver dependencyResolver = ivyAdapter.getDependencyToModuleResolver();
        dependencyResolver = new ClientModuleResolver(dependencyResolver);
        dependencyResolver = new ProjectDependencyResolver(projectModuleRegistry, dependencyResolver);
        DependencyToModuleVersionIdResolver idResolver = new LazyDependencyToModuleResolver(dependencyResolver, ivyAdapter.getResolveData().getSettings().getVersionMatcher());
        ModuleForcingStrategy moduleForcingStrategy = configuration.getResolutionStrategy().getModuleForcingStrategy();
        Set<Action<ForcedModuleDetails>> rules = forcedModuleRulesFactory.createRules(moduleForcingStrategy);
        idResolver = new VersionForcingDependencyToModuleResolver(idResolver, rules);

        ModuleConflictResolver conflictResolver;
        if (configuration.getResolutionStrategy().getConflictResolution() instanceof StrictConflictResolution) {
            conflictResolver = new StrictConflictResolver();
        } else {
            conflictResolver = new LatestModuleConflictResolver();
        }

        DependencyGraphBuilder builder = new DependencyGraphBuilder(moduleDescriptorConverter, resolvedArtifactFactory, idResolver, conflictResolver);
        ResolutionResultBuilder resultBuilder = new ResolutionResultBuilder();
        DefaultLenientConfiguration result = builder.resolve(configuration, ivyAdapter.getResolveData(), resultBuilder);
        return new ResolverResults(new DefaultResolvedConfiguration(result), resultBuilder.getResult());
    }
}
