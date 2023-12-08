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

package org.gradle.caching.internal.controller.service;

import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.controller.operations.LoadOperationDetails;
import org.gradle.caching.internal.controller.operations.LoadOperationHitResult;
import org.gradle.caching.internal.controller.operations.LoadOperationMissResult;
import org.gradle.caching.internal.controller.operations.StoreOperationDetails;
import org.gradle.caching.internal.controller.operations.StoreOperationResult;
import org.gradle.caching.internal.operations.BuildCacheRemoteDisabledProgressDetails;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.IOException;
import java.io.InputStream;

public class OpFiringRemoteBuildCacheServiceHandle extends BaseRemoteBuildCacheServiceHandle {

    private final String buildPath;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildOperationProgressEventEmitter buildOperationProgressEventEmitter;

    public OpFiringRemoteBuildCacheServiceHandle(
        String buildPath,
        BuildCacheService service,
        boolean push,
        BuildCacheServiceRole role,
        BuildOperationExecutor buildOperationExecutor,
        BuildOperationProgressEventEmitter buildOperationProgressEventEmitter,
        boolean logStackTraces,
        boolean disableOnError
    ) {
        super(service, push, role, logStackTraces, disableOnError);
        this.buildPath = buildPath;
        this.buildOperationExecutor = buildOperationExecutor;
        this.buildOperationProgressEventEmitter = buildOperationProgressEventEmitter;
    }

    @Override
    protected void loadInner(final String description, final BuildCacheKey key, final LoadTarget loadTarget) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                loadInner(key, new OpFiringEntryReader(loadTarget));
                context.setResult(
                    loadTarget.isLoaded()
                        ? new LoadOperationHitResult(loadTarget.getLoadedSize())
                        : LoadOperationMissResult.INSTANCE
                );
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(description)
                    .details(new LoadOperationDetails(key))
                    .progressDisplayName("Requesting from remote build cache");
            }
        });
    }

    @Override
    protected void storeInner(final String description, final BuildCacheKey key, final StoreTarget storeTarget) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                OpFiringRemoteBuildCacheServiceHandle.super.storeInner(description, key, storeTarget);
                context.setResult(storeTarget.isStored() ? StoreOperationResult.STORED : StoreOperationResult.NOT_STORED);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(description)
                    .details(new StoreOperationDetails(key, storeTarget.getSize()))
                    .progressDisplayName("Uploading to remote build cache");
            }
        });
    }

    @Override
    protected void onCacheDisabledDueToFailure(BuildCacheKey key, Operation operation, Throwable failure) {
        BuildCacheRemoteDisabledProgressDetails.BuildCacheOperationType operationType = convertToBuildOperationType(operation);
        buildOperationProgressEventEmitter.emitNowIfCurrent(new RemoteDisabledProgressDetails(key, failure, operationType));
    }

    private static BuildCacheRemoteDisabledProgressDetails.BuildCacheOperationType convertToBuildOperationType(Operation operation) {
        switch (operation) {
            case LOAD:
                return BuildCacheRemoteDisabledProgressDetails.BuildCacheOperationType.LOAD;
            case STORE:
                return BuildCacheRemoteDisabledProgressDetails.BuildCacheOperationType.STORE;
            default:
                throw new IllegalStateException();
        }
    }

    private class OpFiringEntryReader implements BuildCacheEntryReader {

        private final BuildCacheEntryReader delegate;

        OpFiringEntryReader(BuildCacheEntryReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public void readFrom(final InputStream input) throws IOException {
            try {
                buildOperationExecutor.run(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        try {
                            delegate.readFrom(input);
                        } catch (IOException e) {
                            throw new UncheckedWrapper(e);
                        }
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Download from remote build cache")
                            .progressDisplayName("Downloading");
                    }
                });
            } catch (UncheckedWrapper uncheckedWrapper) {
                throw uncheckedWrapper.getIOException();
            }
        }
    }

    private static class UncheckedWrapper extends RuntimeException {
        UncheckedWrapper(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        IOException getIOException() {
            return (IOException) getCause();
        }
    }

    private class RemoteDisabledProgressDetails implements BuildCacheRemoteDisabledProgressDetails {
        private final BuildCacheKey key;
        private final Throwable e;
        private final BuildCacheOperationType operationType;

        public RemoteDisabledProgressDetails(BuildCacheKey key, Throwable e, BuildCacheOperationType operationType) {
            this.key = key;
            this.e = e;
            this.operationType = operationType;
        }

        @Override
        public String getBuildPath() {
            return buildPath;
        }

        @Override
        public String getCacheKey() {
            return key.getDisplayName();
        }

        @Override
        public Throwable getFailure() {
            return e;
        }

        @Override
        public BuildCacheOperationType getOperationType() {
            return operationType;
        }
    }
}
