/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.modulecache;

import org.gradle.api.internal.filestore.ArtifactIdentifierFileStore;
import org.gradle.internal.resource.cached.CachedExternalResourceIndex;
import org.gradle.internal.resource.cached.ExternalResourceFileStore;

public class FileStoreAndIndexProvider {
    private final CachedExternalResourceIndex<String> externalResourceIndex;
    private final ExternalResourceFileStore externalResourceFileStore;
    private final ArtifactIdentifierFileStore artifactIdentifierFileStore;

    public FileStoreAndIndexProvider(CachedExternalResourceIndex<String> externalResourceIndex, ExternalResourceFileStore externalResourceFileStore, ArtifactIdentifierFileStore artifactIdentifierFileStore) {
        this.externalResourceIndex = externalResourceIndex;
        this.externalResourceFileStore = externalResourceFileStore;
        this.artifactIdentifierFileStore = artifactIdentifierFileStore;
    }

    public CachedExternalResourceIndex<String> getExternalResourceIndex() {
        return externalResourceIndex;
    }

    public ExternalResourceFileStore getExternalResourceFileStore() {
        return externalResourceFileStore;
    }

    public ArtifactIdentifierFileStore getArtifactIdentifierFileStore() {
        return artifactIdentifierFileStore;
    }
}
