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
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.NextGenBuildCacheService;
import org.gradle.caching.internal.controller.operations.LoadOperationDetails;
import org.gradle.caching.internal.controller.operations.LoadOperationHitResult;
import org.gradle.caching.internal.controller.operations.LoadOperationMissResult;
import org.gradle.caching.internal.controller.operations.StoreOperationDetails;
import org.gradle.caching.internal.controller.operations.StoreOperationResult;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedThreadPoolExecutor;
import org.gradle.internal.file.BufferProvider;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

public class DefaultNextGenBuildCacheAccess implements NextGenBuildCacheAccess {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultNextGenBuildCacheAccess.class);

    private final NextGenBuildCacheHandler local;
    private final NextGenBuildCacheHandler remote;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BufferProvider bufferProvider;
    private final ManagedThreadPoolExecutor remoteProcessor;
    private final ConcurrencyCounter counter;

    public DefaultNextGenBuildCacheAccess(
        NextGenBuildCacheHandler local,
        NextGenBuildCacheHandler remote,
        BuildOperationExecutor buildOperationExecutor,
        BufferProvider bufferProvider,
        ExecutorFactory executorFactory
    ) {
        this.local = local;
        this.remote = remote;
        this.buildOperationExecutor = buildOperationExecutor;
        this.bufferProvider = bufferProvider;
        // TODO Configure this properly
        this.remoteProcessor = executorFactory.createThreadPool("Build cache access", 256, 256, 10, TimeUnit.SECONDS);
        this.counter = new ConcurrencyCounter(remoteProcessor);
    }

    @Override
    public <T> void load(Map<BuildCacheKey, T> entries, LoadHandler<T> handler) {
        CompletableFuture<?>[] asyncLoads = entries.entrySet().stream()
            .flatMap(entry -> {
                BuildCacheKey key = entry.getKey();
                T payload = entry.getValue();
                boolean foundLocally = loadLocally(handler, key, payload);
                if (!foundLocally && remote.canLoad()) {
                    // TODO Improve error handling
                    return Stream.of(CompletableFuture.runAsync(counter.wrap(new RemoteDownload<>(key, payload, handler)), remoteProcessor));
                } else {
                    return Stream.empty();
                }
            })
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(asyncLoads)
            .join();
    }

    private <T> boolean loadLocally(LoadHandler<T> handler, BuildCacheKey key, T payload) {
        return local.canLoad() && local.load(key, input -> handler.handle(input, payload));
    }

    @Override
    public <T> void store(Map<BuildCacheKey, T> entries, StoreHandler<T> handler) {
        CompletableFuture<?>[] asyncStores = entries.entrySet().stream()
            .flatMap(entry -> {
                BuildCacheKey key = entry.getKey();
                T payload = entry.getValue();
                if (!local.canStore()) {
                    // TODO Handle the case when local store is disabled but the remote is not?
                    return Stream.empty();
                }
                if (!local.contains(key)) {
                    local.store(key, handler.handle(payload));
                }
                // TODO Improve error handling
                if (remote.canStore()) {
                    return Stream.of(CompletableFuture.runAsync(counter.wrap(new RemoteUpload(key)), remoteProcessor));
                } else {
                    return Stream.empty();
                }
            })
            .toArray(CompletableFuture[]::new);
        CompletableFuture.allOf(asyncStores)
            .join();
    }

    @Override
    public void close() throws IOException {
        Closer closer = Closer.create();
        closer.register(() -> {
            if (!remoteProcessor.getQueue().isEmpty() || remoteProcessor.getActiveCount() > 0) {
                LOGGER.warn("Waiting for remote cache uploads to finish");
            }
            try {
                remoteProcessor.stop(5, TimeUnit.MINUTES);
            } catch (RuntimeException e) {
                throw new RuntimeException("Couldn't finish uploading all remote entries", e);
            }
        });
        closer.register(local);
        closer.register(remote);
        closer.register(counter);
        closer.close();
    }

    private static class ConcurrencyCounter implements Closeable {
        private final AtomicInteger maxQueueLength = new AtomicInteger(0);
        private final AtomicInteger maxActiveCount = new AtomicInteger(0);

        private final ManagedThreadPoolExecutor processor;

        public ConcurrencyCounter(ManagedThreadPoolExecutor processor) {
            this.processor = processor;
        }

        public Runnable wrap(Runnable delegate) {
            return () -> {
                maxQueueLength.updateAndGet(max -> Integer.max(max, processor.getQueue().size()));
                maxActiveCount.updateAndGet(max -> Integer.max(max, processor.getActiveCount()));
                delegate.run();
            };
        }

        @Override
        public void close() {
            LOGGER.warn("Max concurrency encountered while processing remote cache entries: {}, max queue length: {}",
                maxActiveCount.get(), maxQueueLength.get());
        }
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
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    long size = load();
                    context.setResult(
                        size >= 0
                            ? new LoadOperationHitResult(size)
                            : LoadOperationMissResult.INSTANCE
                    );
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Load entry " + key.getDisplayName() + " from remote build cache")
                        .details(new LoadOperationDetails(key))
                        .progressDisplayName("Requesting from remote build cache");
                }
            });
        }

        private long load() {
            LOGGER.warn("Loading {} from remote", key);
            AtomicLong size = new AtomicLong(-1);
            remote.load(key, input -> {
                // TODO Make this work for large pieces of content, too
                UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
                loadData(input, data);
                LOGGER.warn("Found {} in remote (size: {})", key, data.size());
                size.set(data.size());

                // Mirror data in local cache
                // TODO Do we need to support local push = false?
                if (local.canStore()) {
                    local.store(key, new NextGenBuildCacheService.NextGenWriter() {
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
            return size.get();
        }

        // TODO Do we need to emit this build operation, too?
        private void loadData(InputStream input, UnsynchronizedByteArrayOutputStream output) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    loadDataInternal(input, output);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Download from remote build cache")
                        .progressDisplayName("Downloading");
                }
            });
        }

        private void loadDataInternal(InputStream input, UnsynchronizedByteArrayOutputStream output) {
            try {
                byte[] buffer = bufferProvider.getBuffer();
                IOUtils.copyLarge(input, output, buffer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
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

            // TODO Use a buffer fit for large files
            UnsynchronizedByteArrayOutputStream data = new UnsynchronizedByteArrayOutputStream();
            boolean foundLocally = local.load(key, input -> IOUtils.copyLarge(input, data, bufferProvider.getBuffer()));
            // TODO Handle this better? Do we need to hadnle it at all?
            if (!foundLocally) {
                throw new IllegalStateException("Couldn't store " + key + " in remote cache because it was missing from local cache");
            }

            store(data);
        }

        private void store(UnsynchronizedByteArrayOutputStream data) {
            String description = "Store entry " + key.getDisplayName() + " in remote build cache";
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    boolean stored = storeInner(data);
                    context.setResult(stored
                        ? StoreOperationResult.STORED
                        : StoreOperationResult.NOT_STORED);
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName(description)
                        .details(new StoreOperationDetails(key, data.size()))
                        .progressDisplayName("Uploading to remote build cache");
                }
            });
        }

        private boolean storeInner(UnsynchronizedByteArrayOutputStream data) {
            LOGGER.warn("Storing {} in remote (size: {})", key, data.size());
            AtomicBoolean stored = new AtomicBoolean(false);
            remote.store(key, new NextGenBuildCacheService.NextGenWriter() {
                @Override
                public InputStream openStream() {
                    return data.toInputStream();
                }

                @Override
                public void writeTo(OutputStream output) throws IOException {
                    data.writeTo(output);
                    stored.set(true);
                }

                @Override
                public long getSize() {
                    return data.size();
                }
            });
            return stored.get();
        }
    }
}
