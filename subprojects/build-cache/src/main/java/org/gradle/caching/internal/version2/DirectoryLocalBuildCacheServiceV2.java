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

package org.gradle.caching.internal.version2;

import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DirectoryLocalBuildCacheServiceV2 implements LocalBuildCacheServiceV2 {

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DirectoryLocalBuildCacheServiceV2(PathKeyFileStore fileStore, PersistentCache persistentCache) {
        this.fileStore = fileStore;
        this.persistentCache = persistentCache;
    }

    @Override
    public CacheEntry get(final HashCode key) {
        return persistentCache.withFileLock(new Factory<CacheEntry>() {
            @Nullable
            @Override
            public CacheEntry create() {
                lock.readLock().lock();
                try {
                    LocallyAvailableResource resource = fileStore.get(key.toString());
                    if (resource == null) {
                        return null;
                    }
                    try {
                        InputStream inputStream = new FileInputStream(resource.getFile());
                        try {
                            int type = inputStream.read();
                            switch (type) {
                                case 0: {
                                    return new DefaultFileEntry(resource.getFile());
                                }
                                case 1: {
                                    DataInputStream input = new DataInputStream(inputStream);
                                    ImmutableSortedMap<String, HashCode> entries = readEntries(input);
                                    return new DefaultManifestEntry(entries);
                                }
                                case 2: {
                                    DataInputStream input = new DataInputStream(inputStream);
                                    ImmutableSortedMap<String, HashCode> entries = readEntries(input);
                                    int metadataLength = input.readInt();
                                    byte[] metadata = new byte[metadataLength];
                                    input.readFully(metadata);
                                    return new DefaultResultEntry(entries, metadata);
                                }
                                default:
                                    throw new IllegalStateException("Incorrect data: " + type);
                            }
                        } finally {
                            IOUtils.closeQuietly(inputStream);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                } finally {
                    lock.readLock().unlock();
                }
            }
        });
    }

    private static ImmutableSortedMap<String, HashCode> readEntries(DataInputStream input) throws IOException {
        int count = input.readInt();
        byte[] buffer = new byte[32];
        ImmutableSortedMap.Builder<String, HashCode> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < count; i++) {
            String name = input.readUTF();
            input.readFully(buffer);
            HashCode hash = HashCode.fromBytes(buffer);
            builder.put(name, hash);
        }
        return builder.build();
    }

    private static class DefaultFileEntry implements FileEntry {
        private final File file;

        public DefaultFileEntry(File file) {
            this.file = file;
        }

        @Override
        public InputStream read() throws IOException {
            InputStream inputStream = new FileInputStream(file);
            // Skip header byte
            if (inputStream.read() == -1) {
                throw new IllegalStateException("File entry is missing header byte");
            }
            return inputStream;
        }
    }

    private static class DefaultManifestEntry implements ManifestEntry {
        private final ImmutableSortedMap<String, HashCode> children;

        public DefaultManifestEntry(ImmutableSortedMap<String, HashCode> children) {
            this.children = children;
        }

        @Override
        public ImmutableSortedMap<String, HashCode> getChildren() {
            return children;
        }
    }

    private static class DefaultResultEntry implements ResultEntry {
        private final ImmutableSortedMap<String, HashCode> outputs;
        private final byte[] originMetadata;

        public DefaultResultEntry(ImmutableSortedMap<String, HashCode> outputs, byte[] originMetadata) {
            this.outputs = outputs;
            this.originMetadata = originMetadata;
        }

        @Override
        public ImmutableSortedMap<String, HashCode> getOutputs() {
            return outputs;
        }

        @Override
        public InputStream getOriginMetadata() {
            return new ByteArrayInputStream(originMetadata);
        }
    }
}
