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
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.IOException;
import java.io.InputStream;

public class OpFiringRemoteBuildCacheServiceHandle extends BaseRemoteBuildCacheServiceHandle {

    private final BuildOperationRunner buildOperationRunner;

    public OpFiringRemoteBuildCacheServiceHandle(BuildCacheService service, boolean push, BuildCacheServiceRole role, BuildOperationRunner buildOperationRunner, boolean logStackTraces, boolean disableOnError) {
        super(service, push, role, logStackTraces, disableOnError);
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    protected void loadInner(final String description, final BuildCacheKey key, final LoadTarget loadTarget) {
        buildOperationRunner.run(new RunnableBuildOperation() {
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
        buildOperationRunner.run(new RunnableBuildOperation() {
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

    private class OpFiringEntryReader implements BuildCacheEntryReader {

        private final BuildCacheEntryReader delegate;

        OpFiringEntryReader(BuildCacheEntryReader delegate) {
            this.delegate = delegate;
        }

        @Override
        public void readFrom(final InputStream input) throws IOException {
            try {
                buildOperationRunner.run(new RunnableBuildOperation() {
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
}
