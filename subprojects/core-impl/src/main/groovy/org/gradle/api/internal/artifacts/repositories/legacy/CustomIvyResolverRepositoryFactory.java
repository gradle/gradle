/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.legacy;

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.logging.ProgressLoggerFactory;

public class CustomIvyResolverRepositoryFactory implements LegacyDependencyResolverRepositoryFactory {
    private final ProgressLoggerFactory progressLoggerFactory;
    private final RepositoryCacheManager localCacheManager;
    private final RepositoryCacheManager downloadingCacheManager;

    public CustomIvyResolverRepositoryFactory(ProgressLoggerFactory progressLoggerFactory,
                                              RepositoryCacheManager localCacheManager,
                                              RepositoryCacheManager downloadingCacheManager) {
        this.progressLoggerFactory = progressLoggerFactory;
        this.localCacheManager = localCacheManager;
        this.downloadingCacheManager = downloadingCacheManager;
    }

    public ArtifactRepository createRepository(DependencyResolver dependencyResolver) {
        return new CustomResolverArtifactRepository(dependencyResolver, progressLoggerFactory, localCacheManager, downloadingCacheManager);
    }

}
