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
import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
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
            @Override
            public CacheEntry create() {
                lock.readLock().lock();
                try {
                    LocallyAvailableResource resource = fileStore.get(key.toString());
                    if (resource == null) {
                        return null;
                    }
                    try {
                        DataInputStream input = new DataInputStream(new FileInputStream(resource.getFile()));
                        try {
                            int type = input.readInt();
                            switch (type) {
                                case 0:
                                    return new DefaultFileEntry(resource.getFile());
                                case 1:
                                    return new DefaultManifestEntry(readEntries(input));
                                case 2:
                                    ImmutableSortedMap<String, HashCode> entries = readEntries(input);
                                    int metadataLength = input.readInt();
                                    byte[] metadata = new byte[metadataLength];
                                    input.readFully(metadata);
                                    return new DefaultResultEntry(entries, metadata);
                                default:
                                    throw new IllegalStateException("Incorrect data: " + type);
                            }
                        } finally {
                            IOUtils.closeQuietly(input);
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
        ImmutableSortedMap.Builder<String, HashCode> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < count; i++) {
            String name = input.readUTF();
            int length = input.readInt();
            byte[] buffer = new byte[length];
            input.readFully(buffer);
            HashCode hash = HashCode.fromBytes(buffer);
            builder.put(name, hash);
        }
        return builder.build();
    }

    private static void writeEntries(DataOutputStream output, ImmutableSortedMap<String, HashCode> entries) throws IOException {
        output.writeInt(entries.size());
        for (Map.Entry<String, HashCode> entry : entries.entrySet()) {
            output.writeUTF(entry.getKey());
            byte[] buffer = entry.getValue().toByteArray();
            output.writeInt(buffer.length);
            output.write(buffer);
        }
    }

    @Override
    public FileEntry put(HashCode key, final File file) {
        return put(key, new PutAction<FileEntry>() {
            @Override
            public void writeFile(DataOutputStream output) throws IOException {
                output.writeInt(0);
                FileInputStream input = new FileInputStream(file);
                try {
                    ByteStreams.copy(input, output);
                } finally {
                    IOUtils.closeQuietly(input);
                }
            }

            @Override
            public FileEntry createEntry(File file) {
                return new DefaultFileEntry(file);
            }
        });
    }

    @Override
    public ManifestEntry put(HashCode key, final ImmutableSortedMap<String, HashCode> children) {
        return put(key, new PutAction<ManifestEntry>() {
            @Override
            public void writeFile(DataOutputStream output) throws IOException {
                output.writeInt(1);
                writeEntries(output, children);
            }

            @Override
            public ManifestEntry createEntry(File storedContent) {
                return new DefaultManifestEntry(children);
            }
        });
    }

    @Override
    public ResultEntry put(HashCode key, final ImmutableSortedMap<String, HashCode> outputs, final byte[] originMetadata) {
        return put(key, new PutAction<ResultEntry>() {
            @Override
            public void writeFile(DataOutputStream output) throws IOException {
                output.writeInt(2);
                writeEntries(output, outputs);
                output.writeInt(originMetadata.length);
                output.write(originMetadata);
            }

            @Override
            public ResultEntry createEntry(File storedContent) {
                return new DefaultResultEntry(outputs, originMetadata);
            }
        });
    }

    private <T extends CacheEntry> T put(final HashCode key, final PutAction<T> action) {
        return persistentCache.withFileLock(new Factory<T>() {
            @Override
            public T create() {
                lock.writeLock().lock();
                try {
                    File tempFile = File.createTempFile("build-cache-", ".temp");
                    DataOutputStream output = new DataOutputStream(new FileOutputStream(tempFile));
                    try {
                        action.writeFile(output);
                    } finally {
                        IOUtils.closeQuietly(output);
                    }
                    LocallyAvailableResource resource = fileStore.move(key.toString(), tempFile);
                    return action.createEntry(resource.getFile());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                } finally {
                    lock.writeLock().unlock();
                }
            }
        });
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

        @Override
        public String toString() {
            return "File entry: " + file;
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

        @Override
        public String toString() {
            return "Manifest: " + children;
        }
    }

    private static class DefaultResultEntry implements ResultEntry {
        private final ImmutableSortedMap<String, HashCode> outputs;
        private final byte[] originMetadata;

        public DefaultResultEntry(ImmutableSortedMap<String, HashCode> outputs, byte[] originMetadata) {
            this.outputs = outputs;
            this.originMetadata = originMetadata.clone();
        }

        @Override
        public ImmutableSortedMap<String, HashCode> getOutputs() {
            return outputs;
        }

        @Override
        public InputStream getOriginMetadata() {
            return new ByteArrayInputStream(originMetadata);
        }

        @Override
        public String toString() {
            return "Result: " + outputs;
        }
    }

    private interface PutAction<T extends CacheEntry> {
        void writeFile(DataOutputStream output) throws IOException;
        T createEntry(File storedContent);
    }
}
