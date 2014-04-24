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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.externalresource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

class DefaultExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceArtifactResolver.class);

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder;
    private final List<ResourcePattern> ivyPatterns;
    private final List<ResourcePattern> artifactPatterns;
    private final RepositoryArtifactCache repositoryArtifactCache;
    private final CacheAwareExternalResourceAccessor resourceAccessor;

    public DefaultExternalResourceArtifactResolver(ExternalResourceRepository repository, LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                                   List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, RepositoryArtifactCache repositoryArtifactCache, CacheAwareExternalResourceAccessor resourceAccessor) {
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.ivyPatterns = ivyPatterns;
        this.artifactPatterns = artifactPatterns;
        this.repositoryArtifactCache = repositoryArtifactCache;
        this.resourceAccessor = resourceAccessor;
    }

    public LocallyAvailableExternalResource resolveMetaDataArtifact(ModuleVersionArtifactMetaData artifact) {
        return downloadStaticResource(ivyPatterns, artifact);
    }

    public LocallyAvailableExternalResource resolveArtifact(ModuleVersionArtifactMetaData artifact) {
        return downloadStaticResource(artifactPatterns, artifact);
    }

    public boolean artifactExists(ModuleVersionArtifactMetaData artifact) {
        return staticResourceExists(artifactPatterns, artifact);
    }

    private boolean staticResourceExists(List<ResourcePattern> patternList, ModuleVersionArtifactMetaData artifact) {
        for (ResourcePattern resourcePattern : patternList) {
            String resourcePath = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourcePath);
            try {
                if (repository.getResourceMetaData(resourcePath) != null) {
                    return true;
                }
            } catch (IOException e) {
                throw new ResourceException(String.format("Could not get resource '%s'.", resourcePath), e);
            }
        }
        return false;
    }

    private LocallyAvailableExternalResource downloadStaticResource(List<ResourcePattern> patternList, ModuleVersionArtifactMetaData artifact) {
        for (ResourcePattern resourcePattern : patternList) {
            String resourcePath = resourcePattern.toPath(artifact);
            LOGGER.debug("Loading {}", resourcePath);
            LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(artifact);
            try {
                ExternalResource resource = resourceAccessor.getResource(new URI(resourcePath), localCandidates);
                if (resource != null) {
                    return downloadAndCacheResource(artifact, resource);
                }
            } catch (IOException e) {
                throw new ResourceException(String.format("Could not get resource '%s'.", resourcePath), e);
            } catch (URISyntaxException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        return null;
    }

    private LocallyAvailableExternalResource downloadAndCacheResource(ModuleVersionArtifactMetaData artifact, ExternalResource resource) {
        try {
            return repositoryArtifactCache.downloadAndCacheArtifactFile(artifact, resource);
        } finally {
            try {
                resource.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
