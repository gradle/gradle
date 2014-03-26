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

import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.externalresource.DefaultLocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;

import java.io.File;
import java.io.IOException;

/**
 * A cache manager for local repositories. Doesn't cache anything, and uses artifacts from their origin.
 */
public class LocalFileRepositoryArtifactCache implements RepositoryArtifactCache {

    public boolean isLocal() {
        return true;
    }

    public LocallyAvailableExternalResource downloadAndCacheArtifactFile(ModuleVersionArtifactMetaData artifactId, ExternalResourceDownloader resourceDownloader, ExternalResource resource) throws IOException {
        // Does not download, copy or cache local files.
        assert resource.isLocal();
        File file = new File(resource.getName());
        assert file.isFile();
        return new DefaultLocallyAvailableExternalResource(resource.getName(), new DefaultLocallyAvailableResource(file));
    }
}
