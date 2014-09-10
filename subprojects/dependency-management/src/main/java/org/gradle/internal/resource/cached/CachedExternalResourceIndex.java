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
package org.gradle.internal.resource.cached;

import org.gradle.api.Nullable;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.io.File;

/**
 * Provides an indexed view into cached artifacts and a record of resolution attempts, successful or not.
 *
 * Maintains references to the location of files in the persistent local. Does not deal with moving files into the local.
 * 
 * @param <K> The type of the key to the index
 */
public interface CachedExternalResourceIndex<K> {

    /**
     * Adds a resolution to the index.
     * 
     * The incoming file is expected to be in the persistent local. This method will not move/copy the file there.
     * <p>
     *
     * @param key The key to cache this resolution under in the index. Cannot be null.
     * @param artifactFile The artifact file in the persistent file store. Cannot be null
     * @param metaData Information about this resource at its source
     * @see #storeMissing(Object)
     */
    void store(K key, File artifactFile, @Nullable ExternalResourceMetaData metaData);

    /**
     * Record that the artifact with the given key was missing.
     *
     * @param key The key to cache this resolution under in the index.
     */
    void storeMissing(K key);

    /**
     * Lookup a cached resolution.
     *
     * The {@link CachedExternalResource#getCachedFile()} is guaranteed
     * to exist at the time that the entry is returned from this method.
     *
     * @param key The key to search the index for
     * @return The cached artifact resolution if one exists, otherwise null.
     */
    @Nullable
    CachedExternalResource lookup(K key);

    /**
     * Remove the entry for the given key if it exists.
     *
     * @param key The key of the item to remove.
     */
    void clear(K key);

}
