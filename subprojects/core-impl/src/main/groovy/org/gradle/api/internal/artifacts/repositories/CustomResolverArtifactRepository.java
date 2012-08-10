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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.plugins.repository.Repository;
import org.apache.ivy.plugins.resolver.*;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;

import java.util.List;

public class CustomResolverArtifactRepository extends FixedResolverArtifactRepository {
    private final RepositoryTransportFactory repositoryTransportFactory;

    public CustomResolverArtifactRepository(DependencyResolver resolver, RepositoryTransportFactory repositoryTransportFactory) {
        super(resolver);
        this.repositoryTransportFactory = repositoryTransportFactory;
        configureResolver(resolver, true);
    }

    private void configureResolver(DependencyResolver dependencyResolver, boolean isTopLevel) {
        if (isTopLevel) {
            if (resolver instanceof AbstractResolver && !(resolver instanceof FileSystemResolver)) {
                ((AbstractResolver) resolver).setRepositoryCacheManager(repositoryTransportFactory.getDownloadingCacheManager());
            }
        }

        if (dependencyResolver instanceof FileSystemResolver) {
            ((FileSystemResolver) dependencyResolver).setLocal(true);
            ((FileSystemResolver) dependencyResolver).setRepositoryCacheManager(repositoryTransportFactory.getLocalCacheManager());
        }
        if (dependencyResolver instanceof RepositoryResolver) {
            Repository repository = ((RepositoryResolver) dependencyResolver).getRepository();
            repositoryTransportFactory.attachListener(repository);
        }
        if (dependencyResolver instanceof DualResolver) {
            DualResolver dualResolver = (DualResolver) dependencyResolver;
            configureResolver(dualResolver.getIvyResolver(), false);
            configureResolver(dualResolver.getArtifactResolver(), false);
        }
        if (dependencyResolver instanceof ChainResolver) {
            ChainResolver chainResolver = (ChainResolver) dependencyResolver;
            @SuppressWarnings("unchecked") List<DependencyResolver> resolvers = chainResolver.getResolvers();
            for (DependencyResolver resolver : resolvers) {
                configureResolver(resolver, false);
            }
        }
    }

}
