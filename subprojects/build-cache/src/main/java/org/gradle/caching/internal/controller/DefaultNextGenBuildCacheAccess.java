/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.internal.controller;

import com.google.common.io.Closer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DefaultNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNextGenBuildCacheAccess.class);

    // TODO Move all thread-local buffers to a shared service
    // TODO Make buffer size configurable
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final ThreadLocal<byte[]> COPY_BUFFERS = ThreadLocal.withInitial(() -> new byte[BUFFER_SIZE]);
    private static final CompletableFuture<?>[] EMPTY_COMPLETABLE_FUTURE_ARRAY = new CompletableFuture<?>[0];

    private final NextGenBuildCacheHandler local;
    private final NextGenBuildCacheHandler remote;

    private final ThreadPoolExecutor remoteProcessor;

    public DefaultNextGenBuildCacheAccess(NextGenBuildCacheHandler local, NextGenBuildCacheHandler remote) {
        this.local = local;
        this.remote = remote;
        // TODO Configure this properly
        this.remoteProcessor = new ThreadPoolExecutor(16, 256, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    }

    @Override
    public <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler) {
        List<CompletableFuture<Void>> remoteDownloads = new ArrayList<>();
        entries.forEach((key, payload) -> {
            boolean foundLocally = local.canLoad() && local.load(key, input -> handler.handle(input, payload));
            if (!foundLocally && remote.canLoad()) {
                remoteDownloads.add(CompletableFuture.runAsync(new RemoteDownload<>(key, payload, handler), remoteProcessor));
            }
        });
        // TODO Add error handling
        CompletableFuture.allOf(remoteDownloads.toArray(EMPTY_COMPLETABLE_FUTURE_ARRAY))
            .join();
    }

    @Override
    public <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler) {
        entries.forEach((key, payload) -> {
            if (!local.canStore()) {
                // TODO Handle the case when local store is disabled
                return;
            }
            if (!local.contains(key)) {
                local.store(key, handler.handle(payload));
            }
            // TODO Add error handling
            if (remote.canStore()) {
                remoteProcessor.submit(new RemoteUpload(key));
            }
        });
    }

    @Override
    public void close() throws IOException {
        Closer closer = Closer.create();
        closer.register(() -> {
            LOGGER.debug("Shutting down remote cache processor with {} active jobs and {} queue length", remoteProcessor.getActiveCount(), remoteProcessor.getQueue().size());
            // TODO Handle this better
            remoteProcessor.shutdown();
            try {
                if (!remoteProcessor.awaitTermination(5, TimeUnit.MINUTES)) {
                    throw new RuntimeException("Couldn't finish uploading all remote entries");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Couldn't finish uploading all remote entries", e);
            }
        });
        closer.register(local);
        closer.register(remote);
        closer.close();
    }

    private class RemoteDownload<T> implements Runnable {
        private final BuildCacheKey key;
        private final T payload;
        private final LoadHandler<T> handler;

        public RemoteDownload(BuildCacheKey key, T payload, LoadHandler<T> handler) {
            this.key = key;
            this.payload = payload;
            this.handler = handler;
        }

        @Override
        public void run() {
            LOGGER.warn("Loading {} from remote", key);
            remote.load(key, input -> {
                LOGGER.warn("Found {} in remote", key);
                // TODO Make this work for large pieces of content, too
                UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
                byte[] buffer = COPY_BUFFERS.get();
                IOUtils.copyLarge(input, data, buffer);

                // Mirror data in local cache
                // TODO Do we need to support local push = false?
                if (local.canStore()) {
                    local.store(key, new BuildCacheEntryWriter() {
                        @Override
                        public InputStream openStream() {
                            return data.toInputStream();
                        }

                        @Override
                        public void writeTo(OutputStream output) throws IOException {
                            data.writeTo(output);
                        }

                        @Override
                        public long getSize() {
                            return data.size();
                        }
                    });
                    handler.handle(data.toInputStream(), payload);
                } else {
                    handler.handle(input, payload);
                }
            });
        }
    }

    private class RemoteUpload implements Runnable {
        private final BuildCacheKey key;

        public RemoteUpload(BuildCacheKey key) {
            this.key = key;
        }

        @Override
        public void run() {
            // TODO Check contains only above a threshold
            if (remote.contains(key)) {
                LOGGER.warn("Not storing {} in remote", key);
                return;
            }

            LOGGER.warn("Storing {} in remote", key);
            boolean stored = local.load(key, input -> {
                // TODO Pass size here so we don't need to copy data to memory first
                UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
                IOUtils.copyLarge(input, data, COPY_BUFFERS.get());
                remote.store(key, new BuildCacheEntryWriter() {
                    @Override
                    public InputStream openStream() {
                        return data.toInputStream();
                    }

                    @Override
                    public long getSize() {
                        return data.size();
                    }
                });
            });

            // TODO Handle this better? Do we need to hadnle it at all?
            if (!stored) {
                throw new IllegalStateException("Couldn't store " + key + " in remote cache because it was missing from local cache");
            }
        }
    }
}
