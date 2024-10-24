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

package org.gradle.caching.internal.controller.service;

import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.operations.BuildCacheLocalLoadBuildOperationType;
import org.gradle.caching.internal.operations.BuildCacheLocalStoreBuildOperationType;
import org.gradle.caching.local.internal.LocalBuildCacheService;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class OpFiringLocalBuildCacheServiceHandle extends BaseLocalBuildCacheServiceHandle {
    private static final BuildCacheLocalStoreBuildOperationType.Result LOCAL_STORE_RESULT = new BuildCacheLocalStoreBuildOperationType.Result() {
        @Override
        public boolean isStored() {
            return true;
        }
    };

    private final BuildOperationRunner buildOperationRunner;

    public OpFiringLocalBuildCacheServiceHandle(LocalBuildCacheService service, boolean pushEnabled, BuildOperationRunner buildOperationRunner) {
        super(service, pushEnabled);
        this.buildOperationRunner = buildOperationRunner;
    }

    @Override
    public Optional<BuildCacheLoadResult> maybeLoad(BuildCacheKey key, Function<File, BuildCacheLoadResult> unpackFunction) {
        return buildOperationRunner.call(new CallableBuildOperation<Optional<BuildCacheLoadResult>>() {
            @Override
            public Optional<BuildCacheLoadResult> call(BuildOperationContext context) {
                AtomicReference<Long> archiveSize = new AtomicReference<>();
                Optional<BuildCacheLoadResult> result = OpFiringLocalBuildCacheServiceHandle.super.maybeLoad(key, file -> {
                    archiveSize.set(file.length());
                    return unpackFunction.apply(file);
                });
                context.setResult(new LocalLoadResult(result, archiveSize));
                return result;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Load entry " + key.getHashCode() + " from local build cache")
                    .details(new LocalLoadDetails(key));
            }
        });
    }

    @Override
    protected void storeInner(BuildCacheKey key, File file) {
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) throws Exception {
                OpFiringLocalBuildCacheServiceHandle.super.storeInner(key, file);
                context.setResult(LOCAL_STORE_RESULT);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Store entry " + key.getHashCode() + " in local build cache")
                    .details(new LocalStoreDetails(key, file));
            }
        });
    }

    private static class LocalLoadDetails implements BuildCacheLocalLoadBuildOperationType.Details {

        private final BuildCacheKey key;

        public LocalLoadDetails(BuildCacheKey key) {
            this.key = key;
        }

        @Override
        public String getCacheKey() {
            return key.getHashCode();
        }
    }

    private static class LocalLoadResult implements BuildCacheLocalLoadBuildOperationType.Result {
        private final Optional<BuildCacheLoadResult> result;
        private final AtomicReference<Long> archiveSize;

        public LocalLoadResult(Optional<BuildCacheLoadResult> result, AtomicReference<Long> archiveSize) {
            this.result = result;
            this.archiveSize = archiveSize;
        }

        @Override
        public boolean isHit() {
            return result.isPresent();
        }

        @Override
        public long getArchiveSize() {
            Long l = archiveSize.get();
            if (l != null) {
                return l;
            }
            return -1;
        }
    }

    private static class LocalStoreDetails implements BuildCacheLocalStoreBuildOperationType.Details {
        private final BuildCacheKey key;
        private final long archiveSize;

        public LocalStoreDetails(BuildCacheKey key, File file) {
            this.key = key;
            // We need to calculate the size eagerly here, since the file will already be gone
            // (aka in the local cache), when the DV plugin queries the value.
            this.archiveSize = file.length();
        }

        @Override
        public String getCacheKey() {
            return key.getHashCode();
        }

        @Override
        public long getArchiveSize() {
            return archiveSize;
        }
    }
}
