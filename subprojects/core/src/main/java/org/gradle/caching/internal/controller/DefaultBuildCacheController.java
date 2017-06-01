/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.CountingInputStream;
import com.google.common.io.CountingOutputStream;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.BuildCacheLoadBuildOperationType;
import org.gradle.caching.internal.BuildCacheStoreBuildOperationType;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultBuildCacheController implements BuildCacheController {

    @VisibleForTesting
    static final int MAX_ERRORS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildCacheController.class);

    private final ServiceHandle local;
    private final ServiceHandle remote;

    private final BuildOperationExecutor buildOperationExecutor;
    private final TemporaryFileProvider temporaryFileProvider;
    private final boolean logStackTraces;

    public DefaultBuildCacheController(
        @Nullable BuildCacheServiceRef local,
        @Nullable BuildCacheServiceRef remote,
        BuildOperationExecutor buildOperationExecutor,
        TemporaryFileProvider temporaryFileProvider,
        boolean logStackTraces
    ) {
        this.local = toHandle(local, BuildCacheServiceRole.LOCAL);
        this.remote = toHandle(remote, BuildCacheServiceRole.REMOTE);
        this.buildOperationExecutor = buildOperationExecutor;
        this.temporaryFileProvider = temporaryFileProvider;
        this.logStackTraces = logStackTraces;
    }

    private ServiceHandle toHandle(BuildCacheServiceRef ref, BuildCacheServiceRole role) {
        return ref == null ? null : new ServiceHandle(role, ref);
    }

    @Override
    public void load(BuildCacheLoadOp loadOp) {
        if (local != null && local.canLoad()) {
            local.doLoad(loadOp);
        }
        if (!loadOp.isLoaded() && remote != null && remote.canLoad()) {
            remote.doLoad(loadOp);
        }
    }

    @Override
    public void store(BuildCacheStoreOp storeOp) {
        boolean localStore = local != null && local.canStore();
        boolean remoteStore = remote != null && remote.canStore();

        if (localStore && remoteStore) {
            doStoreBoth(storeOp);
        } else if (localStore) {
            local.doStore(storeOp);
        } else if (remoteStore) {
            remote.doStore(storeOp);
        }
    }

    private void doStoreBoth(BuildCacheStoreOp storeOp) {
        File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
        try {
            // TODO: demarcate this with an operation.
            doTmpFileStore(storeOp, destination);
            local.doStore(storeOp, destination);
            remote.doStore(storeOp, destination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            GFileUtils.deleteQuietly(destination);
        }
    }

    private void doTmpFileStore(BuildCacheStoreOp storeOp, File destination) throws IOException {
        OutputStream fileOutputStream = null;
        try {
            fileOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
            storeOp.store(fileOutputStream);
        } catch (FileNotFoundException e) {
            throw new BuildCacheException("Couldn't create local file for cache entry", e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    @Override
    public void close() {
        try {
            close(local);
        } finally {
            close(remote);
        }
    }

    private void close(ServiceHandle handle) {
        if (handle != null) {
            try {
                handle.close();
            } catch (Exception e) {
                if (logStackTraces) {
                    LOGGER.warn("Error closing {} build cache: ", handle.role.getDisplayName(), e);
                } else {
                    LOGGER.warn("Error closing {} build cache: {}", handle.role.getDisplayName(), e.getMessage());
                }
            }
        }
    }

    private class ServiceHandle implements Closeable {

        private final BuildCacheServiceRole role;

        private final BuildCacheService service;
        private final boolean pushEnabled;
        private final AtomicInteger errorCount = new AtomicInteger();

        private final AtomicReference<String> disabledMessage = new AtomicReference<String>();
        private boolean closed;

        private ServiceHandle(BuildCacheServiceRole role, BuildCacheServiceRef serviceRef) {
            this.role = role;
            this.service = serviceRef.service;
            this.pushEnabled = serviceRef.pushEnabled;
        }

        private boolean canLoad() {
            return disabledMessage.get() == null;
        }

        private void doLoad(final BuildCacheLoadOp loadOp) {
            final String description = "Loading entry " + loadOp.getKey() + " from " + role.getDisplayName() + " build cache";
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    try {
                        LOGGER.debug(description);
                        CountingBuildCacheEntryReader reader = new CountingBuildCacheEntryReader(loadOp);
                        service.load(loadOp.getKey(), reader);
                        BuildCacheLoadBuildOperationType.Result result = loadOp.isLoaded()
                            ? new LoadOperationHitResult(reader.bytes, loadOp)
                            : LoadOperationMissResult.INSTANCE;
                        context.setResult(result);
                    } catch (Exception e) {
                        context.failed(e);
                        if (e instanceof BuildCacheException) {
                            reportFailure("load", "from", loadOp.getKey(), e);
                            recordFailure();
                        } else {
                            throw new GradleException("Could not load entry " + loadOp.getKey() + " from " + role.getDisplayName() + " build cache", e);
                        }
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName(description)
                        .details(new LoadOperationDetails(ServiceHandle.this, loadOp));
                }
            });
        }

        private boolean canStore() {
            return pushEnabled && disabledMessage.get() == null;
        }

        private void doStore(final BuildCacheStoreOp storeOp) {
            doStore(storeOp, new Factory<BuildCacheStoreBuildOperationType.Result>() {
                @Override
                public BuildCacheStoreBuildOperationType.Result create() {
                    OpBackedCountingBuildCacheEntryWriter countingWriter = new OpBackedCountingBuildCacheEntryWriter(storeOp);
                    service.store(storeOp.getKey(), countingWriter);
                    return storeOp.isStored()
                        ? new StoreOperationResult(countingWriter.bytes, storeOp)
                        : NoStoreOperationResult.INSTANCE;
                }
            });
        }

        @SuppressWarnings("Duplicates")
        private void doStore(final BuildCacheStoreOp storeOp, final File file) {
            // TODO: indicate that this operation does not include “packing” the archive
            doStore(storeOp, new Factory<BuildCacheStoreBuildOperationType.Result>() {
                @Override
                public BuildCacheStoreBuildOperationType.Result create() {
                    FileCopyBuildCacheEntryWriter fileWriter = new FileCopyBuildCacheEntryWriter(file);
                    service.store(storeOp.getKey(), fileWriter);
                    return fileWriter.copied
                        ? new StoreOperationResult(file.length(), storeOp)
                        : NoStoreOperationResult.INSTANCE;
                }
            });
        }

        @SuppressWarnings("Duplicates")
        private void doStore(final BuildCacheStoreOp storeOp, final Factory<BuildCacheStoreBuildOperationType.Result> resultFactory) {
            final String description = "Storing entry " + storeOp.getKey() + " in " + role.getDisplayName() + " build cache";
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    try {
                        context.setResult(resultFactory.create());
                    } catch (Exception e) {
                        context.failed(e);
                        reportFailure("store", "in", storeOp.getKey(), e);
                        if (e instanceof BuildCacheException) {
                            recordFailure();
                        } else {
                            // TODO: somehow indicate via the operation result that this was fatal?
                            // Alternatively, the scan server side infrastructure can make this determination based on the exception.
                            disable("a non-recoverable error was encountered.");
                        }
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName(description)
                        .details(new StoreOperationDetails(ServiceHandle.this, storeOp));
                }
            });
        }

        private void recordFailure() {
            if (errorCount.incrementAndGet() == MAX_ERRORS) {
                disable(MAX_ERRORS + " recoverable errors were encountered.");
            }
        }

        private void reportFailure(String verb, String preposition, BuildCacheKey key, Throwable e) {
            if (!LOGGER.isWarnEnabled()) {
                return;
            }
            if (logStackTraces) {
                LOGGER.warn("Could not {} entry {} {} {} build cache", verb, key, preposition, role.getDisplayName(), e);
            } else {
                LOGGER.warn("Could not {} entry {} {} {} build cache: {}", verb, key, preposition, role.getDisplayName(), e.getMessage());
            }
        }

        private void disable(String message) {
            // TODO: fire operation indicating the cache was disabled
            if (disabledMessage.compareAndSet(null, message)) {
                LOGGER.warn("The {} build cache is now disabled because {}", role.getDisplayName(), message);
            }
        }

        @Override
        public void close() throws IOException {
            LOGGER.debug("Closing {} build cache", role.getDisplayName());
            if (!closed) {
                String disableMessage = disabledMessage.get();
                if (disableMessage != null) {
                    LOGGER.warn("The {} build cache was disabled during the build because {}", role.getDisplayName(), disableMessage);
                }
                try {
                    service.close();
                } finally {
                    closed = true;
                }
            }
        }

    }

    private class CountingBuildCacheEntryReader implements BuildCacheEntryReader {

        private final BuildCacheLoadOp loadOp;
        private long bytes;

        private CountingBuildCacheEntryReader(BuildCacheLoadOp loadOp) {
            this.loadOp = loadOp;
        }

        @Override
        public void readFrom(InputStream input) throws IOException {
            CountingInputStream countingInputStream = new CountingInputStream(input);
            loadOp.load(countingInputStream);
            bytes = countingInputStream.getCount();
        }

    }

    private class OpBackedCountingBuildCacheEntryWriter implements BuildCacheEntryWriter {

        private final BuildCacheStoreOp storeOp;
        private long bytes;

        private OpBackedCountingBuildCacheEntryWriter(BuildCacheStoreOp storeOp) {
            this.storeOp = storeOp;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            CountingOutputStream countingOutputStream = new CountingOutputStream(output);
            storeOp.store(countingOutputStream);
            bytes = countingOutputStream.getCount();
        }

    }

    private class FileCopyBuildCacheEntryWriter implements BuildCacheEntryWriter {

        private final File file;
        boolean copied;

        private FileCopyBuildCacheEntryWriter(File file) {
            this.file = file;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            Files.copy(file, output);
            copied = true;
        }
    }

    private static class LoadOperationDetails implements BuildCacheLoadBuildOperationType.Details {
        private final ServiceHandle serviceHandle;
        private final BuildCacheLoadOp loadOp;

        private LoadOperationDetails(ServiceHandle serviceHandle, BuildCacheLoadOp loadOp) {
            this.serviceHandle = serviceHandle;
            this.loadOp = loadOp;
        }

        @Override
        public String getRole() {
            return serviceHandle.role.getDisplayName();
        }

        @Override
        public String getCacheKey() {
            return loadOp.getKey().getHashCode();
        }
    }

    private static class LoadOperationHitResult implements BuildCacheLoadBuildOperationType.Result {
        private final long bytes;

        private final BuildCacheLoadOp loadOp;

        private LoadOperationHitResult(long bytes, BuildCacheLoadOp loadOp) {
            this.bytes = bytes;
            this.loadOp = loadOp;
        }

        @Override
        public long getArchiveSize() {
            return bytes;
        }

        @Override
        public long getArchiveEntryCount() {
            return loadOp.getArtifactEntryCount();
        }

    }

    private static class LoadOperationMissResult implements BuildCacheLoadBuildOperationType.Result {

        private static final BuildCacheLoadBuildOperationType.Result INSTANCE = new LoadOperationMissResult();

        private LoadOperationMissResult() {
        }

        @Override
        public long getArchiveSize() {
            return 0;
        }

        @Override
        public long getArchiveEntryCount() {
            return 0;
        }

    }

    private static class StoreOperationDetails implements BuildCacheLoadBuildOperationType.Details {
        private final ServiceHandle serviceHandle;
        private final BuildCacheStoreOp storeOp;

        private StoreOperationDetails(ServiceHandle serviceHandle, BuildCacheStoreOp storeOp) {
            this.serviceHandle = serviceHandle;
            this.storeOp = storeOp;
        }

        @Override
        public String getRole() {
            return serviceHandle.role.getDisplayName();
        }

        @Override
        public String getCacheKey() {
            return storeOp.getKey().getHashCode();
        }
    }

    private static class StoreOperationResult implements BuildCacheStoreBuildOperationType.Result {

        private final long bytes;
        private final BuildCacheStoreOp storeOp;

        private StoreOperationResult(long bytes, BuildCacheStoreOp storeOp) {
            this.bytes = bytes;
            this.storeOp = storeOp;
        }

        @Override
        public long getArchiveSize() {
            return bytes;
        }

        @Override
        public long getArchiveEntryCount() {
            return storeOp.getArtifactEntryCount();
        }
    }

    private static class NoStoreOperationResult implements BuildCacheStoreBuildOperationType.Result {

        private static final BuildCacheStoreBuildOperationType.Result INSTANCE = new NoStoreOperationResult();

        private NoStoreOperationResult() {
        }

        @Override
        public long getArchiveSize() {
            return 0;
        }

        @Override
        public long getArchiveEntryCount() {
            return 0;
        }
    }
}
