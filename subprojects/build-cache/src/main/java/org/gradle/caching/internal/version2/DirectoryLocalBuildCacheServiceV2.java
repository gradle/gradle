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

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.ImmutableSortedMap;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Action;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.cache.PersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoAction;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.PathKeyFileStore;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@NonNullApi
public class DirectoryLocalBuildCacheServiceV2 implements LocalBuildCacheServiceV2 {
    private static final int BUFFER_SIZE = 4096;
    private static final ThreadLocal<byte[]> ENCODER_BUFFERS = new ThreadLocal<byte[]>() {
        @Override
        protected byte[] initialValue() {
            return new byte[BUFFER_SIZE];
        }
    };
    private static final int CODE_FILE = 0;
    private static final int CODE_MANIFEST = 1;
    private static final int CODE_RESULT = 2;

    private final PathKeyFileStore fileStore;
    private final PersistentCache persistentCache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public DirectoryLocalBuildCacheServiceV2(PathKeyFileStore fileStore, PersistentCache persistentCache) {
        this.fileStore = fileStore;
        this.persistentCache = persistentCache;
    }

    private static Input wrap(InputStream inputStream) {
        Input input = new Input();
        input.setBuffer(ENCODER_BUFFERS.get());
        input.setInputStream(inputStream);
        return input;
    }

    private static IoAction<OutputStream> wrap(final IoAction<Output> action) {
        return new IoAction<OutputStream>() {
            @Override
            public void execute(OutputStream outputStream) throws IOException {
                Output output = new Output();
                output.setBuffer(ENCODER_BUFFERS.get());
                output.setOutputStream(outputStream);
                try {
                    action.execute(output);
                } finally {
                    output.flush();
                }
            }
        };
    }

    @Nullable
    @Override
    public Result getResult(HashCode key) {
        return get(key, new EntryReader<Result>() {
            @Override
            public Result readEntry(int code, InputStream inputStream) {
                if (code != CODE_RESULT) {
                    throw new IllegalStateException("Incorrect data: " + code);
                }
                Input input = wrap(inputStream);
                final ImmutableSortedMap<String, HashCode> outputs = readEntries(input);
                int metadataLength = input.readInt();
                final byte[] metadata = input.readBytes(metadataLength);
                return new Result() {
                    @Override
                    public ImmutableSortedMap<String, HashCode> getOutputs() {
                        return outputs;
                    }

                    @Override
                    public InputStream getOriginMetadata() {
                        return new ByteArrayInputStream(metadata);
                    }
                };
            }
        });
    }

    @Override
    public void getContent(HashCode key, final ContentProcessor contentProcessor) {
        get(key, new EntryReader<Void>() {
            @Override
            public Void readEntry(int code, InputStream inputStream) throws IOException {
                switch (code) {
                    case CODE_FILE:
                        contentProcessor.processFile(new GZIPInputStream(inputStream));
                        break;
                    case CODE_MANIFEST:
                        contentProcessor.processManifest(readEntries(wrap(inputStream)));
                        break;
                    default:
                        throw new IllegalStateException("Incorrect data: " + code);
                }
                return null;
            }
        });
    }

    @Nullable
    private <T> T get(final HashCode key, final EntryReader<T> entryReader) {
        return persistentCache.withFileLock(new Factory<T>() {
            @Override
            public T create() {
                lock.readLock().lock();
                try {
                    LocallyAvailableResource resource = fileStore.get(key.toString());
                    if (resource == null) {
                        return null;
                    }
                    try {
                        InputStream input = new FileInputStream(resource.getFile());
                        try {
                            int code = input.read();
                            return entryReader.readEntry(code, input);
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

    private static ImmutableSortedMap<String, HashCode> readEntries(Input input) {
        int count = input.readInt();
        ImmutableSortedMap.Builder<String, HashCode> builder = ImmutableSortedMap.naturalOrder();
        for (int i = 0; i < count; i++) {
            String name = input.readString();
            int length = input.readInt();
            byte[] buffer = input.readBytes(length);
            HashCode hash = HashCode.fromBytes(buffer);
            builder.put(name, hash);
        }
        return builder.build();
    }

    private static void writeEntries(Output output, ImmutableSortedMap<String, HashCode> entries) {
        output.writeInt(entries.size());
        for (Map.Entry<String, HashCode> entry : entries.entrySet()) {
            output.writeString(entry.getKey());
            byte[] buffer = entry.getValue().toByteArray();
            output.writeInt(buffer.length);
            output.write(buffer);
        }
    }

    @Override
    public void putFile(HashCode key, final IoAction<OutputStream> writer) {
        put(key, CODE_FILE, new IoAction<OutputStream>() {
            @Override
            @SuppressWarnings("Since15")
            public void execute(OutputStream outputStream) throws IOException {
                GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream, 4096);
                writer.execute(gzipOutputStream);
                gzipOutputStream.finish();
            }
        });
    }

    @Override
    public void putManifest(HashCode key, final ImmutableSortedMap<String, HashCode> children) {
        put(key, CODE_MANIFEST, wrap(new IoAction<Output>() {
            @Override
            public void execute(Output output) {
                writeEntries(output, children);
            }
        }));
    }

    @Override
    public void putResult(HashCode key, final ImmutableSortedMap<String, HashCode> outputs, final byte[] originMetadata) {
        put(key, CODE_RESULT, wrap(new IoAction<Output>() {
            @Override
            public void execute(Output output) {
                writeEntries(output, outputs);
                output.writeInt(originMetadata.length);
                output.write(originMetadata);
            }
        }));
    }

    private void put(final HashCode hashCode, final int code, final IoAction<OutputStream> action) {
        persistentCache.withFileLock(new Runnable() {
            @Override
            public void run() {
                String key = hashCode.toString();
                lock.writeLock().lock();
                try {
                    fileStore.add(key, new Action<File>() {
                        @Override
                        public void execute(File file) {
                            try {
                                OutputStream output = new FileOutputStream(file);
                                try {
                                    output.write(code);
                                    action.execute(output);
                                } finally {
                                    IOUtils.closeQuietly(output);
                                }
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
                } finally {
                    lock.writeLock().unlock();
                }
            }
        });
    }

    private interface EntryReader<T> {
        T readEntry(int code, InputStream inputStream) throws IOException;
    }
}
