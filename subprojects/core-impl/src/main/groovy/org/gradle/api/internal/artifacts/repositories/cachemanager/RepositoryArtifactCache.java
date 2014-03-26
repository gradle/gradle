/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;

import java.io.File;
import java.io.IOException;

/**
 * This is a transitional interface for moving away from the Ivy RepositoryCacheManager.
 */
public interface RepositoryArtifactCache {
    boolean isLocal();

    /**
     * Downloads the artifact file, moving it into the correct location in the cache.
     *
     * @param artifact The id of the artifact this resource represents
     * @param resourceDownloader An action to use for downloading the resource
     * @param resource The artifact resource
     * @return The cached resource
     */
    LocallyAvailableExternalResource downloadAndCacheArtifactFile(ModuleVersionArtifactMetaData artifact, ExternalResourceDownloader resourceDownloader, ExternalResource resource) throws IOException;

    interface ExternalResourceDownloader {
        public void download(ExternalResource resource, File dest) throws IOException;
    }
}
