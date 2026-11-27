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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.UrlBackedArtifactMetadata;
import org.gradle.internal.component.model.ModuleDescriptorArtifactMetadata;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor.DefaultResourceFileStore;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.regex.Pattern;

class DefaultExternalResourceArtifactResolver implements ExternalResourceArtifactResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceArtifactResolver.class);
    private static final Pattern PATH_PARAMETERS = Pattern.compile(";[^/]*");

    private final ExternalResourceRepository repository;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final List<ResourcePattern> ivyPatterns;
    private final List<ResourcePattern> artifactPatterns;
    private final FileStore<ModuleComponentArtifactIdentifier> fileStore;
    private final CacheAwareExternalResourceAccessor resourceAccessor;

    public DefaultExternalResourceArtifactResolver(ExternalResourceRepository repository, LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, FileStore<ModuleComponentArtifactIdentifier> fileStore, CacheAwareExternalResourceAccessor resourceAccessor) {
        this.repository = repository;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.ivyPatterns = ivyPatterns;
        this.artifactPatterns = artifactPatterns;
        this.fileStore = fileStore;
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public LocallyAvailableExternalResource resolveArtifact(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        if (artifact instanceof ModuleDescriptorArtifactMetadata) {
            return downloadStaticResource(ivyPatterns, artifact, result);
        }
        return downloadStaticResource(artifactPatterns, artifact, result);
    }

    @Override
    public boolean artifactExists(ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        return staticResourceExists(artifactPatterns, artifact, result);
    }

    private boolean staticResourceExists(List<ResourcePattern> patternList, ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        for (ResourcePattern resourcePattern : patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue;
            }
            ExternalResourceName location = resourcePattern.getLocation(artifact);
            result.attempted(location);
            LOGGER.debug("Loading {}", location);
            try {
                if (repository.resource(location, true).getMetaData() != null) {
                    return true;
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(location.getUri(), e);
            }
        }
        return false;
    }

    private LocallyAvailableExternalResource downloadStaticResource(List<ResourcePattern> patternList, final ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        if (artifact instanceof UrlBackedArtifactMetadata) {
            UrlBackedArtifactMetadata urlArtifact = (UrlBackedArtifactMetadata) artifact;
            return downloadByUrl(patternList, urlArtifact, result);
        } else {
            return downloadByCoords(patternList, artifact, result);
        }
    }

    private LocallyAvailableExternalResource downloadByUrl(List<ResourcePattern> patternList, final UrlBackedArtifactMetadata artifact, ResourceAwareResolveResult result) {
        String relativeUrl = normalizeRelativeUrl(artifact);
        // A repository can have several patterns with different roots, so the same URL can be outside the root of one pattern and inside the root of another
        boolean outsideRootOfSomePattern = false;
        boolean insideRootOfSomePattern = false;
        for (ResourcePattern resourcePattern : patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue;
            }
            ExternalResourceName moduleDir = resourcePattern.toModuleVersionPath(normalizeComponentId(artifact));
            ExternalResourceName location = locateInRepository(resourcePattern, moduleDir, relativeUrl);
            if (location == null) {
                outsideRootOfSomePattern = true;
                continue;
            }
            insideRootOfSomePattern = true;
            result.attempted(location);
            LOGGER.debug("Loading {}", location);
            LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(artifact);
            try {
                LocallyAvailableExternalResource resource = resourceAccessor.getResource(location, artifact.getId().getDestinationFileName(), getFileStore(artifact), localCandidates);
                if (resource != null) {
                    return resource;
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(location.getUri(), e);
            }
        }
        if (outsideRootOfSomePattern && !insideRootOfSomePattern) {
            throw new InvalidUserDataException(String.format("Cannot locate file '%s' of %s as its URL '%s' designates a location outside the repository.", artifact.getFileName(), artifact.getComponentId().getDisplayName(), relativeUrl));
        }
        return null;
    }

    /**
     * The URL of a file is resolved against the directory of its module version and may designate another directory, for example that of another version.
     * It must not be able to select something the host of the repository serves outside the repository.
     *
     * @return null when the URL designates a location outside the root of the given pattern.
     */
    @Nullable
    private static ExternalResourceName locateInRepository(ResourcePattern resourcePattern, ExternalResourceName moduleDir, String relativeUrl) {
        try {
            ExternalResourceName root = resourcePattern.toRootPath();
            ExternalResourceName location = moduleDir.resolve(relativeUrl);
            if (!location.isSameOrDescendantOf(root)) {
                return null;
            }
            if (relativeUrl.indexOf(';') < 0) {
                return location;
            }
            // Some servers remove the parameters of a path element before they handle '..', so that '..;x' designates the parent directory
            ExternalResourceName locationWithoutParameters = moduleDir.resolve(PATH_PARAMETERS.matcher(relativeUrl).replaceAll(""));
            if (locationWithoutParameters.isSameOrDescendantOf(root)) {
                return location;
            }
        } catch (IllegalArgumentException e) {
            // The URL designates a location above the root of the host
        }
        return null;
    }

    private String normalizeRelativeUrl(UrlBackedArtifactMetadata artifact) {
        ModuleComponentIdentifier componentId = artifact.getComponentId();
        if (componentId instanceof MavenUniqueSnapshotComponentIdentifier) {
            // We need to replace the `-SNAPSHOT` in the relative URL but only for the version part
            MavenUniqueSnapshotComponentIdentifier snapshotComponentId = (MavenUniqueSnapshotComponentIdentifier) componentId;
            return artifact.getRelativeUrl().replace("-" + snapshotComponentId.getSnapshotVersion(), "-" + snapshotComponentId.getTimestampedVersion());
        }
        return artifact.getRelativeUrl();
    }

    private ModuleComponentIdentifier normalizeComponentId(UrlBackedArtifactMetadata artifact) {
        ModuleComponentIdentifier rawId = artifact.getComponentId();
        if (rawId instanceof MavenUniqueSnapshotComponentIdentifier) {
            // We cannot use a Maven unique snapshot id for the path part
            return ((MavenUniqueSnapshotComponentIdentifier) rawId).getSnapshotComponent();
        }
        return rawId;
    }

    private LocallyAvailableExternalResource downloadByCoords(List<ResourcePattern> patternList, final ModuleComponentArtifactMetadata artifact, ResourceAwareResolveResult result) {
        for (ResourcePattern resourcePattern : patternList) {
            if (isIncomplete(resourcePattern, artifact)) {
                continue;
            }
            ExternalResourceName location = resourcePattern.getLocation(artifact);
            result.attempted(location);
            LOGGER.debug("Loading {}", location);
            LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(artifact);
            try {
                LocallyAvailableExternalResource resource = resourceAccessor.getResource(location, null, getFileStore(artifact), localCandidates);
                if (resource != null) {
                    return resource;
                }
            } catch (Exception e) {
                throw ResourceExceptions.getFailed(location.getUri(), e);
            }
        }
        return null;
    }

    private CacheAwareExternalResourceAccessor.ResourceFileStore getFileStore(final ModuleComponentArtifactMetadata artifact) {
        return new DefaultResourceFileStore<ModuleComponentArtifactIdentifier>(fileStore) {
            @Override
            protected ModuleComponentArtifactIdentifier computeKey() {
                return artifact.getId();
            }
        };
    }

    private boolean isIncomplete(ResourcePattern resourcePattern, ModuleComponentArtifactMetadata artifact) {
        return !resourcePattern.isComplete(artifact);
    }
}
