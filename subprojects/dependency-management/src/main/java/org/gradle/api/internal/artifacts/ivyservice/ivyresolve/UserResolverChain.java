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

package org.gradle.api.internal.artifacts.ivyservice.ivyresolve;

import org.gradle.api.Transformer;
import org.gradle.api.internal.artifacts.ComponentSelectionRulesInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetaData;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentResolver;

public class UserResolverChain implements RepositoryChain {
    private final RepositoryChainDependencyResolver dependencyResolver;
    private final RepositoryChainArtifactResolver artifactResolver = new RepositoryChainArtifactResolver();
    private final RepositoryChainAdapter adapter;
    private final DynamicVersionResolver dynamicVersionResolver;

    public UserResolverChain(VersionMatcher versionMatcher, LatestStrategy latestStrategy, ComponentSelectionRulesInternal versionSelectionRules) {
        NewestVersionComponentChooser componentChooser = new NewestVersionComponentChooser(latestStrategy, versionMatcher, versionSelectionRules);
        ModuleTransformer metaDataFactory = new ModuleTransformer();
        dependencyResolver = new RepositoryChainDependencyResolver(componentChooser, metaDataFactory);
        dynamicVersionResolver = new DynamicVersionResolver(componentChooser, metaDataFactory);
        adapter = new RepositoryChainAdapter(dynamicVersionResolver, dependencyResolver, versionMatcher);
    }

    public DependencyToComponentIdResolver getComponentIdResolver() {
        return adapter;
    }

    public ComponentMetaDataResolver getComponentMetaDataResolver() {
        return adapter;
    }

    public DependencyToComponentResolver getDependencyResolver() {
        return dependencyResolver;
    }

    public ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    public void add(ModuleComponentRepository repository) {
        dependencyResolver.add(repository);
        dynamicVersionResolver.add(repository);
        artifactResolver.add(repository);
    }

    private static class ModuleTransformer implements Transformer<ModuleComponentResolveMetaData, RepositoryChainModuleResolution> {
        public ModuleComponentResolveMetaData transform(RepositoryChainModuleResolution original) {
            RepositoryChainModuleSource moduleSource = new RepositoryChainModuleSource(original.repository.getId(), original.module.getSource());
            original.module.setSource(moduleSource);
            return original.module;
        }
    }
}
