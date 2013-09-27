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

package org.gradle.api.internal.externalresource.cached;

import org.gradle.api.internal.artifacts.ivyservice.CacheLockingManager;
import org.gradle.api.internal.externalresource.ivy.AbstractCachedIndex;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.messaging.serialize.DefaultSerializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.File;
import java.io.Serializable;

public class DefaultCachedExternalResourceIndex<K extends Serializable> extends AbstractCachedIndex<K, CachedExternalResource> implements CachedExternalResourceIndex<K> {

    private final BuildCommencedTimeProvider timeProvider;

    public DefaultCachedExternalResourceIndex(String persistentCacheFile, Class<K> keyType, BuildCommencedTimeProvider timeProvider, CacheLockingManager cacheLockingManager) {
        super(persistentCacheFile, new DefaultSerializer<K>(keyType.getClassLoader()), new DefaultSerializer<CachedExternalResource>(CachedExternalResource.class.getClassLoader()), cacheLockingManager);
        this.timeProvider = timeProvider;
    }

    private DefaultCachedExternalResource createEntry(File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        return new DefaultCachedExternalResource(artifactFile, timeProvider.getCurrentTime(), externalResourceMetaData);
    }

    public void store(final K key, final File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        assertArtifactFileNotNull(artifactFile);
        assertKeyNotNull(key);

        storeInternal(key, createEntry(artifactFile, externalResourceMetaData));
    }

    public void storeMissing(K key) {
        storeInternal(key, new DefaultCachedExternalResource(timeProvider.getCurrentTime()));
    }
}
