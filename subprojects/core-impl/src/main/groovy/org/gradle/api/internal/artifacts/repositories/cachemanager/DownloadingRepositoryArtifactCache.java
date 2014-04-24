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
package org.gradle.api.internal.artifacts.repositories.cachemanager;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.resource.ResourceException;
import org.gradle.internal.Factory;
import org.gradle.internal.filestore.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

/**
 * A cache manager for remote repositories, that downloads files and stores them in the FileStore provided.
 */
public class DownloadingRepositoryArtifactCache implements RepositoryArtifactCache {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadingRepositoryArtifactCache.class);

    private final FileStore<ModuleVersionArtifactMetaData> fileStore;
    private final CachedExternalResourceIndex<String> artifactUrlCachedResolutionIndex;
    private final TemporaryFileProvider temporaryFileProvider;
    private final CacheLockingManager cacheLockingManager;

    public DownloadingRepositoryArtifactCache(FileStore<ModuleVersionArtifactMetaData> fileStore, CachedExternalResourceIndex<String> artifactUrlCachedResolutionIndex,
                                              TemporaryFileProvider temporaryFileProvider, CacheLockingManager cacheLockingManager) {
        this.fileStore = fileStore;
        this.artifactUrlCachedResolutionIndex = artifactUrlCachedResolutionIndex;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cacheLockingManager = cacheLockingManager;
    }

    public boolean isLocal() {
        return false;
    }

    public LocallyAvailableExternalResource downloadAndCacheArtifactFile(final ModuleVersionArtifactMetaData artifact, final ExternalResource resource) {
        final File destination = temporaryFileProvider.createTemporaryFile("gradle_download", "bin");
        try {
            try {
                try {
                    LOGGER.debug("Downloading {} to {}", resource.getName(), destination);
                    if (destination.getParentFile() != null) {
                        GFileUtils.mkdirs(destination.getParentFile());
                    }
                    resource.writeTo(destination);
                } finally {
                    resource.close();
                }
            } catch (IOException e) {
                throw new ResourceException(String.format("Failed to download resource '%s'.", resource.getName()), e);
            }
            return cacheLockingManager.useCache(String.format("Store %s", artifact), new Factory<LocallyAvailableExternalResource>() {
                public LocallyAvailableExternalResource create() {
                    LocallyAvailableResource cachedResource = fileStore.move(artifact, destination);
                    File fileInFileStore = cachedResource.getFile();
                    ExternalResourceMetaData metaData = resource.getMetaData();
                    artifactUrlCachedResolutionIndex.store(metaData.getLocation(), fileInFileStore, metaData);
                    return new DefaultLocallyAvailableExternalResource(resource.getURI(), cachedResource, metaData);
                }
            });
        } finally {
            destination.delete();
        }
    }

}
