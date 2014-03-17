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
import org.gradle.api.internal.artifacts.ivyservice.ArtifactResolver;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionMetaData;

public class UserResolverChain implements RepositoryChain {
    private final RepositoryChainDependencyResolver dependencyResolver;
    private final RepositoryChainArtifactResolver artifactResolver = new RepositoryChainArtifactResolver();

    public UserResolverChain(VersionMatcher versionMatcher, LatestStrategy latestStrategy) {
        this.dependencyResolver = new RepositoryChainDependencyResolver(versionMatcher, latestStrategy, new ModuleTransformer());
    }

    public DependencyToModuleVersionResolver getDependencyResolver() {
        return dependencyResolver;
    }

    public ArtifactResolver getArtifactResolver() {
        return artifactResolver;
    }

    public void add(LocalAwareModuleVersionRepository repository) {
        dependencyResolver.add(repository);
        artifactResolver.add(repository);
    }

    private static class ModuleTransformer implements Transformer<ModuleVersionMetaData, RepositoryChainModuleResolution> {
        public ModuleVersionMetaData transform(RepositoryChainModuleResolution original) {
            RepositoryChainModuleSource moduleSource = new RepositoryChainModuleSource(original.repository.getId(), original.moduleSource);
            return original.module.withSource(moduleSource);
        }
    }
}
