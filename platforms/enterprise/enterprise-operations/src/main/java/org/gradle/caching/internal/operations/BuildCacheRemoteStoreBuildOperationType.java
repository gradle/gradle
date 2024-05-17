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

package org.gradle.caching.internal.operations;

import org.gradle.internal.operations.BuildOperationType;

/**
 * A store operation to a build cache.
 *
 * A store operation may actually store or fail.
 * Store operation results and failures are mutually exclusive.
 */
public final class BuildCacheRemoteStoreBuildOperationType implements BuildOperationType<BuildCacheRemoteStoreBuildOperationType.Details, BuildCacheRemoteStoreBuildOperationType.Result> {

    public interface Details {

        /**
         * The cache key.
         */
        String getCacheKey();

        /**
         * The number of bytes of the stored cache artifact.
         */
        long getArchiveSize();

    }

    public interface Result {

        boolean isStored();

    }

    private BuildCacheRemoteStoreBuildOperationType() {
    }
}
