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

import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transport.ExternalResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

class DefaultExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceArtifactResolver.class);

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final List<ResourcePattern> ivyPatterns;
    private final List<ResourcePattern> artifactPatterns;
    private final FileStore<ModuleComponentArtifactIdentifier> fileStore;
    private final CacheAwareExternalResourceAccessor resourceAccessor;

    public DefaultExternalResourceArtifactResolver(ExternalResourceRepository repository, LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                                   List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, FileStore<ModuleComponentArtifactIdentifier> fileStore, CacheAwareExternalResourceAccessor resourceAccessor) {
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.ivyPatterns = ivyPatterns;
        this.artifactPatterns = artifactPatterns;
        this.fileStore = fileStore;
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ModuleSource getSource() {
        return null;
    }

    public LocallyAvailableExternalResource resolveArtifact(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        if (artifact instanceof ModuleDescriptorArtifactMetadata) {
            return downloadStaticResource(ivyPatterns, artifact, result);
        }
        return downloadStaticResource(artifactPatterns, artifact, result);
    }

    public boolean artifactExists(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        return staticResourceExists(artifactPatterns, artifact, result);
    }

    private boolean staticResourceExists(List<ResourcePattern> patternList, ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        for (ResourcePattern resourcePattern : patternList) {
            ExternalResourceName location = resourcePattern.getLocation(artifact);
            result.attempted(location);
            LOGGER.debug("Loading {}", location);
            try {
                if (repository.getResourceMetaData(location.getUri(), true) != null) {
                    return true;
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(location.getUri(), e);
            }
        }
        return false;
    }

    private LocallyAvailableExternalResource downloadStaticResource(List<ResourcePattern> patternList, final ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        for (ResourcePattern resourcePattern : patternList) {
            ExternalResourceName location = resourcePattern.getLocation(artifact);
            result.attempted(location);
            LOGGER.debug("Loading {}", location);
            LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(artifact);
            try {
                LocallyAvailableExternalResource resource = resourceAccessor.getResource(location.getUri(), new CacheAwareExternalResourceAccessor.ResourceFileStore() {
                    public LocallyAvailableResource moveIntoCache(File downloadedResource) {
                        return fileStore.move(artifact.getId(), downloadedResource);
                    }
                }, localCandidates);
                if (resource != null) {
                    return resource;
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(location.getUri(), e);
            }
        }
        return null;
    }
}
