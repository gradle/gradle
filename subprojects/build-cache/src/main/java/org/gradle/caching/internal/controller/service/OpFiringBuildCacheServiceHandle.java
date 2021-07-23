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

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheEntryInternal;
import org.gradle.caching.internal.BuildCacheLoadOutcomeInternal;
import org.gradle.caching.internal.BuildCacheServiceInternal;
import org.gradle.caching.internal.BuildCacheStoreOutcomeInternal;
import org.gradle.caching.internal.controller.operations.LoadOperationDetails;
import org.gradle.caching.internal.controller.operations.LoadOperationHitResult;
import org.gradle.caching.internal.controller.operations.LoadOperationMissResult;
import org.gradle.caching.internal.controller.operations.StoreOperationDetails;
import org.gradle.caching.internal.controller.operations.StoreOperationResult;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;

import java.io.File;

public class OpFiringBuildCacheServiceHandle extends BaseBuildCacheServiceHandle {

    private final BuildOperationExecutor buildOperationExecutor;

    public OpFiringBuildCacheServiceHandle(BuildCacheServiceInternal service, boolean push, BuildCacheServiceRole role, BuildOperationExecutor buildOperationExecutor, boolean logStackTraces, boolean disableOnError) {
        super(service, push, role, logStackTraces, disableOnError);
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    protected BuildCacheLoadOutcomeInternal loadInner(final String description, final BuildCacheKey key, final BuildCacheEntryInternal entry) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheLoadOutcomeInternal>() {
            @Override
            public BuildCacheLoadOutcomeInternal call(BuildOperationContext context) {
                OpFiringBuildCacheEntry opFiringEntry = new OpFiringBuildCacheEntry(entry);
                BuildCacheLoadOutcomeInternal loadOutcome;
                try {
                    loadOutcome = OpFiringBuildCacheServiceHandle.super.loadInner(key, opFiringEntry);
                } finally {
                    if (opFiringEntry.downloadingOperationContext != null) {
                        opFiringEntry.downloadingOperationContext.setResult(null);
                    }
                }
                context.setResult(
                    loadOutcome == BuildCacheLoadOutcomeInternal.LOADED
                        ? new LoadOperationHitResult(entry.getFile().length())
                        : LoadOperationMissResult.INSTANCE
                );
                return loadOutcome;
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
    protected BuildCacheStoreOutcomeInternal storeInner(final String description, final BuildCacheKey key, final BuildCacheEntryInternal entry) {
        return buildOperationExecutor.call(new CallableBuildOperation<BuildCacheStoreOutcomeInternal>() {
            @Override
            public BuildCacheStoreOutcomeInternal call(BuildOperationContext context) {
                BuildCacheStoreOutcomeInternal storeOutcome = OpFiringBuildCacheServiceHandle.super.storeInner(description, key, entry);
                context.setResult(
                    storeOutcome == BuildCacheStoreOutcomeInternal.STORED
                        ? StoreOperationResult.STORED
                        : StoreOperationResult.NOT_STORED
                );
                return storeOutcome;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(description)
                    .details(new StoreOperationDetails(key, entry.getFile().length()))
                    .progressDisplayName("Uploading to remote build cache");
            }
        });
    }

    private class OpFiringBuildCacheEntry implements BuildCacheEntryInternal {

        private final BuildCacheEntryInternal delegate;
        private BuildOperationContext downloadingOperationContext;

        OpFiringBuildCacheEntry(BuildCacheEntryInternal delegate) {
            this.delegate = delegate;
        }

        @Override
        public File getFile() {
            return delegate.getFile();
        }

        @Override
        public void markDownloading() {
            if (downloadingOperationContext != null) {
                downloadingOperationContext = buildOperationExecutor.start(BuildOperationDescriptor.displayName("Download from remote build cache")
                    .progressDisplayName("Downloading"));
            }
        }

    }

}
