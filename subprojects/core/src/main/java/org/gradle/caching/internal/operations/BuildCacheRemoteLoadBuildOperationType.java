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
 * A load operation from a build cache.
 *
 * A load operation may HIT or MISS.
 * It may also fail.
 * Load operation results and failures are mutually exclusive.
 */
public final class BuildCacheRemoteLoadBuildOperationType implements BuildOperationType<BuildCacheRemoteLoadBuildOperationType.Details, BuildCacheRemoteLoadBuildOperationType.Result> {

    public interface Details {

        /**
         * The cache key.
         */
        String getCacheKey();

    }

    public interface Result {

        boolean isHit();

        /**
         * The number of bytes of the loaded cache artifact if it was a hit.
         * Else undetermined.
         */
        long getArchiveSize();

    }

    private BuildCacheRemoteLoadBuildOperationType() {
    }
}
