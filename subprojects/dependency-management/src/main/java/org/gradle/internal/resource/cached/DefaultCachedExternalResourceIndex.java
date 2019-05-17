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

package org.gradle.internal.resource.cached;

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCacheLockingManager;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.local.FileAccessTracker;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.util.BuildCommencedTimeProvider;

import java.io.File;
import java.io.Serializable;
import java.net.URI;

public class DefaultCachedExternalResourceIndex<K extends Serializable> extends AbstractCachedIndex<K, CachedExternalResource> implements CachedExternalResourceIndex<K> {
    private static final CachedExternalResourceSerializer CACHED_EXTERNAL_RESOURCE_SERIALIZER = new CachedExternalResourceSerializer();
    private final BuildCommencedTimeProvider timeProvider;

    public DefaultCachedExternalResourceIndex(String persistentCacheFile, Serializer<K> keySerializer, BuildCommencedTimeProvider timeProvider, ArtifactCacheLockingManager artifactCacheLockingManager, FileAccessTracker fileAccessTracker) {
        super(persistentCacheFile, keySerializer, CACHED_EXTERNAL_RESOURCE_SERIALIZER, artifactCacheLockingManager, fileAccessTracker);
        this.timeProvider = timeProvider;
    }

    private DefaultCachedExternalResource createEntry(File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        return new DefaultCachedExternalResource(artifactFile, timeProvider.getCurrentTime(), externalResourceMetaData);
    }

    @Override
    public void store(final K key, final File artifactFile, ExternalResourceMetaData externalResourceMetaData) {
        assertArtifactFileNotNull(artifactFile);
        assertKeyNotNull(key);

        storeInternal(key, createEntry(artifactFile, externalResourceMetaData));
    }

    @Override
    public void storeMissing(K key) {
        storeInternal(key, new DefaultCachedExternalResource(timeProvider.getCurrentTime()));
    }

    private static class CachedExternalResourceSerializer implements Serializer<CachedExternalResource> {
        @Override
        public CachedExternalResource read(Decoder decoder) throws Exception {
            File cachedFile = null;
            if (decoder.readBoolean()) {
                cachedFile = new File(decoder.readString());
            }
            long cachedAt = decoder.readLong();
            ExternalResourceMetaData metaData = null;
            if (decoder.readBoolean()) {
                URI uri = new URI(decoder.readString());
                long lastModified = 0;
                if (decoder.readBoolean()) {
                    lastModified = decoder.readLong();
                }
                String contentType = decoder.readNullableString();
                long contentLength = decoder.readSmallLong();
                String etag = decoder.readNullableString();
                HashValue sha1 = null;
                if (decoder.readBoolean()) {
                    sha1 = HashValue.parse(decoder.readString());
                }
                metaData = new DefaultExternalResourceMetaData(uri, lastModified, contentLength, contentType, etag, sha1);
            }
            return new DefaultCachedExternalResource(cachedFile, cachedAt, metaData);
        }

        @Override
        public void write(Encoder encoder, CachedExternalResource value) throws Exception {
            encoder.writeBoolean(value.getCachedFile() != null);
            if (value.getCachedFile() != null) {
                encoder.writeString(value.getCachedFile().getAbsolutePath());
            }
            encoder.writeLong(value.getCachedAt());
            ExternalResourceMetaData metaData = value.getExternalResourceMetaData();
            encoder.writeBoolean(metaData != null);
            if (metaData != null) {
                encoder.writeString(metaData.getLocation().toASCIIString());
                encoder.writeBoolean(metaData.getLastModified() != null);
                if (metaData.getLastModified() != null) {
                    encoder.writeLong(metaData.getLastModified().getTime());
                }
                encoder.writeNullableString(metaData.getContentType());
                encoder.writeSmallLong(metaData.getContentLength());
                encoder.writeNullableString(metaData.getEtag());
                encoder.writeBoolean(metaData.getSha1() != null);
                if (metaData.getSha1() != null) {
                    encoder.writeString(metaData.getSha1().asHexString());
                }
            }
        }
    }
}
