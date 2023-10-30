/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.modulecache.artifacts;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.cached.CachedExternalResource;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;

public interface ModuleArtifactCache {
    /**
     * Adds a resolution to the index.
     *
     * The incoming file is expected to be in the persistent local. This method will not move/copy the file there. <p>
     *  @param key The key to cache this resolution under in the index. Cannot be null.
     * @param artifactFile The artifact file in the persistent file store. Cannot be null
     * @param moduleDescriptorHash The checksum (SHA1) of the related moduledescriptor.
     */
    void store(ArtifactAtRepositoryKey key, File artifactFile, HashCode moduleDescriptorHash);

    /**
     * Record that the artifact with the given key was missing.
     *  @param key The key to cache this resolution under in the index.
     * @param descriptorHash The SHA1 hash of the related moduleDescriptor
     */
    void storeMissing(ArtifactAtRepositoryKey key, List<String> attemptedLocations, HashCode descriptorHash);

    /**
     * Lookup a cached resolution.
     *
     * The {@link CachedExternalResource#getCachedFile()} is guaranteed to exist at the time that the entry is returned from this method.
     *
     * @param key The key to search the index for
     * @return The cached artifact resolution if one exists, otherwise null.
     */
    @Nullable
    CachedArtifact lookup(ArtifactAtRepositoryKey key);

    /**
     * Remove the entry for the given key if it exists.
     *
     * @param key The key of the item to remove.
     */
    void clear(ArtifactAtRepositoryKey key);
}
