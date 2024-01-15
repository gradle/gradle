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
 * A load operation from the local build cache.
 *
 * A load operation may HIT or MISS.
 * It may also fail.
 * Load operation results and failures are mutually exclusive.
 *
 * @since 8.6
 */
public final class BuildCacheLocalLoadBuildOperationType implements BuildOperationType<BuildCacheLocalLoadBuildOperationType.Details, BuildCacheLocalLoadBuildOperationType.Result> {

    public interface Details {

        /**
         * The cache key.
         *
         * @since 8.6
         */
        String getCacheKey();

    }

    public interface Result {

        /**
         * Whether the load has been a hit.
         *
         * @since 8.6
         */
        boolean isHit();

        /**
         * The number of bytes of the cache artifact if it was a hit.
         * Else undetermined.
         *
         * @since 8.6
         */
        long getArchiveSize();

    }

    private BuildCacheLocalLoadBuildOperationType() {
    }
}
