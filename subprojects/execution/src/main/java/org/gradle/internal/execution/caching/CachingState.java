/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution.caching;

import com.google.common.collect.ImmutableList;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.Either;

import java.util.Optional;

public interface CachingState {
    /**
     * The cache key if a valid cache key could be built.
     */
    Either<ImmutableList<CachingDisabledReason>, BuildCacheKey> getKey();

    /**
     * Individual fingerprints for each of the work's inputs.
     */
    Optional<CachingInputs> getInputs();

    CachingState NOT_DETERMINED = new CachingState() {
        private final Either<ImmutableList<CachingDisabledReason>, BuildCacheKey> reason = Either.left(ImmutableList.of(
            new CachingDisabledReason(CachingDisabledReasonCatwgory.UNKNOWN, "Cacheability was not determined")));

        @Override
        public Either<ImmutableList<CachingDisabledReason>, BuildCacheKey> getKey() {
            return reason;
        }

        @Override
        public Optional<CachingInputs> getInputs() {
            return Optional.empty();
        }
    };

    CachingState BUILD_CACHE_DISABLED = new CachingState() {
        private final Either<ImmutableList<CachingDisabledReason>, BuildCacheKey> reason = Either.left(ImmutableList.of(
            new CachingDisabledReason(CachingDisabledReasonCatwgory.BUILD_CACHE_DISABLED, "Build cache is disabled")));

        @Override
        public Either<ImmutableList<CachingDisabledReason>, BuildCacheKey> getKey() {
            return reason;
        }

        @Override
        public Optional<CachingInputs> getInputs() {
            return Optional.empty();
        }
    };
}
