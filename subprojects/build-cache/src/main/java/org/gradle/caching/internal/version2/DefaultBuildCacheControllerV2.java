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

import org.gradle.api.NonNullApi;
import org.gradle.caching.internal.controller.operations.PackOperationDetails;
import org.gradle.caching.internal.controller.operations.PackOperationResult;
import org.gradle.caching.internal.controller.operations.UnpackOperationDetails;
import org.gradle.caching.internal.controller.operations.UnpackOperationResult;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import javax.annotation.Nullable;

@NonNullApi
public class DefaultBuildCacheControllerV2 implements BuildCacheControllerV2 {

    private BuildOperationExecutor buildOperationExecutor;

    public DefaultBuildCacheControllerV2(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public <T> T load(final BuildCacheLoadCommandV2<T> command) {
        return buildOperationExecutor.call(new CallableBuildOperation<T>() {
            @Nullable
            @Override
            public T call(BuildOperationContext context) {
                BuildCacheLoadCommandV2.Result<T> result = command.load();
                context.setResult(new UnpackOperationResult(result.getArtifactEntryCount()));
                return result.getMetadata();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Load build cache entry " + command.getKey())
                    .details(new UnpackOperationDetails(command.getKey(), 0))
                    .progressDisplayName("Load build cache entry");
            }
        });
    }

    @Override
    public void store(final BuildCacheStoreCommandV2 command) {
        buildOperationExecutor.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                BuildCacheStoreCommandV2.Result result = command.store();
                context.setResult(new PackOperationResult(
                    result.getArtifactEntryCount(),
                    0
                ));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Storing build cache entry in local cache " + command.getKey())
                    .details(new PackOperationDetails(command.getKey()))
                    .progressDisplayName("Storing build cache entry");
            }
        });
    }
}
