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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.apache.ivy.plugins.version.VersionMatcher;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.conflicts.StrictConflictResolution;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.util.WrapUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultDependencyResolver implements ArtifactDependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDependencyResolver.class);
    private final ModuleDescriptorConverter moduleDescriptorConverter;
    private final ResolvedArtifactFactory resolvedArtifactFactory;
    private final ResolveIvyFactory ivyFactory;

    public DefaultDependencyResolver(ResolveIvyFactory ivyFactory, ModuleDescriptorConverter moduleDescriptorConverter, ResolvedArtifactFactory resolvedArtifactFactory) {
        this.ivyFactory = ivyFactory;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.resolvedArtifactFactory = resolvedArtifactFactory;
    }

    public ResolvedConfiguration resolve(ConfigurationInternal configuration) throws ResolveException {
        LOGGER.debug("Resolving {}", configuration);

        Ivy ivy = ivyFactory.create(configuration.getResolutionStrategy());
        DependencyResolver resolver = ivy.getSettings().getDefaultResolver();

        ResolveOptions options = new ResolveOptions();
        options.setDownload(false);
        options.setConfs(WrapUtil.toArray(configuration.getName()));
        ResolveData resolveData = new ResolveData(ivy.getResolveEngine(), options);
        VersionMatcher versionMatcher = ivy.getSettings().getVersionMatcher();

        DependencyToModuleResolver dependencyResolver = new IvyResolverBackedDependencyToModuleResolver(ivy, resolveData, resolver, versionMatcher);
        dependencyResolver = new VersionForcingDependencyToModuleResolver(dependencyResolver, configuration.getResolutionStrategy().getForcedModules());
        IvyResolverBackedArtifactToFileResolver artifactResolver = new IvyResolverBackedArtifactToFileResolver(resolver);

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
}
