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
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.BuildCacheLoadBuildOperationType;
import org.gradle.caching.internal.BuildCacheStoreBuildOperationType;
import org.gradle.caching.internal.controller.operations.LoadOperationDetails;
import org.gradle.caching.internal.controller.operations.LoadOperationHitResult;
import org.gradle.caching.internal.controller.operations.LoadOperationMissResult;
import org.gradle.caching.internal.controller.operations.StoreOperationDetails;
import org.gradle.caching.internal.controller.operations.StoreOperationResult;
import org.gradle.internal.Factory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import java.io.Closeable;
import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class BuildCacheServiceHandle implements Closeable {

    private static final Logger LOGGER = Logging.getLogger(BuildCacheServiceHandle.class);

    @VisibleForTesting
    final BuildCacheService service;

    private final BuildCacheServiceRole role;
    private final boolean pushEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private final boolean logStackTraces;

    private final AtomicInteger errorCount = new AtomicInteger();

    private final AtomicReference<String> disabledMessage = new AtomicReference<String>();
    private boolean closed;

    BuildCacheServiceHandle(BuildCacheServiceRef serviceRef, BuildCacheServiceRole role, BuildOperationExecutor buildOperationExecutor, boolean logStackTraces) {
        this.role = role;
        this.service = serviceRef.service;
        this.pushEnabled = serviceRef.pushEnabled;
        this.buildOperationExecutor = buildOperationExecutor;
        this.logStackTraces = logStackTraces;
    }

    boolean canLoad() {
        return disabledMessage.get() == null;
    }

    <T> T doLoad(final BuildCacheLoadCommand<T> command) {
        final String description = "Loading entry " + command.getKey() + " from " + role.getDisplayName() + " build cache";
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Override
            public T call(BuildOperationContext context) {
                try {
                    LOGGER.debug(description);
                    CommandBackedEntryReader<T> entryReader = new CommandBackedEntryReader<T>(command);
                    service.load(command.getKey(), entryReader);
                    BuildCacheLoadBuildOperationType.Result operationResult = entryReader.result == null
                        ? LoadOperationMissResult.INSTANCE
                        : new LoadOperationHitResult(entryReader.bytes, entryReader.result.getArtifactEntryCount());
                    context.setResult(operationResult);

                    if (entryReader.result == null) {
                        return null;
                    } else {
                        T metadata = entryReader.result.getMetadata();
                        if (metadata == null) {
                            throw new IllegalStateException("Build cache load command " + command + " returned null metadata");
                        }
                        return metadata;
                    }
                } catch (Exception e) {
                    // Use the raw exception as the failure, not the wrapped
                    context.failed(e);

                    if (e instanceof BuildCacheException) {
                        reportFailure("load", "from", command.getKey(), e);
                        recordFailure();
                        return null;
                    } else {
                        throw new GradleException("Could not load entry " + command.getKey() + " from " + role.getDisplayName() + " build cache", e);
                    }
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(description)
                    .details(new LoadOperationDetails(role, command));
            }
        });
    }

    boolean canStore() {
        return pushEnabled && disabledMessage.get() == null;
    }

    void doStore(final BuildCacheStoreCommand command) {
        doStoreInner(command.getKey(), new Factory<BuildCacheStoreBuildOperationType.Result>() {
            @Override
            public BuildCacheStoreBuildOperationType.Result create() {
                CommandBackedEntryWriter entryWriter = new CommandBackedEntryWriter(command);
                service.store(command.getKey(), entryWriter);
                if (entryWriter.result == null) {
                    throw noStoreException();
                } else {
                    return new StoreOperationResult(entryWriter.bytes, entryWriter.result.getArtifactEntryCount());
                }
            }
        });
    }

    void doStore(final BuildCacheKey key, final File file, final BuildCacheStoreCommand.Result storeResult) {
        // TODO: indicate that this operation does not include “packing” the archive
        doStoreInner(key, new Factory<BuildCacheStoreBuildOperationType.Result>() {
            @Override
            public BuildCacheStoreBuildOperationType.Result create() {
                FileCopyBuildCacheEntryWriter fileWriter = new FileCopyBuildCacheEntryWriter(file);
                service.store(key, fileWriter);
                if (fileWriter.copied) {
                    return new StoreOperationResult(file.length(), storeResult.getArtifactEntryCount());
                } else {
                    throw noStoreException();
                }
            }
        });
    }

    private void doStoreInner(final BuildCacheKey key, final Factory<BuildCacheStoreBuildOperationType.Result> resultFactory) {
        final String description = "Storing entry " + key + " in " + role.getDisplayName() + " build cache";
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                try {
                    BuildCacheStoreBuildOperationType.Result result = resultFactory.create();
                    context.setResult(result);
                } catch (Exception e) {
                    context.failed(e);

                    reportFailure("store", "in", key, e);
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
                    .details(new StoreOperationDetails(key, role));
            }
        });
    }

    private IllegalStateException noStoreException() {
        return new IllegalStateException("Store operation of " + role.getDisplayName() + " build cache completed without storing the artifact");
    }

    private void recordFailure() {
        if (errorCount.incrementAndGet() == DefaultBuildCacheController.MAX_ERRORS) {
            disable(DefaultBuildCacheController.MAX_ERRORS + " recoverable errors were encountered.");
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
    public void close() {
        LOGGER.debug("Closing {} build cache", role.getDisplayName());
        if (!closed) {
            String disableMessage = disabledMessage.get();
            if (disableMessage != null) {
                LOGGER.warn("The {} build cache was disabled during the build because {}", role.getDisplayName(), disableMessage);
            }
            try {
                service.close();
            } catch (Exception e) {
                if (logStackTraces) {
                    LOGGER.warn("Error closing {} build cache: ", role.getDisplayName(), e);
                } else {
                    LOGGER.warn("Error closing {} build cache: {}", role.getDisplayName(), e.getMessage());
                }
            } finally {
                closed = true;
            }
        }
    }

}
