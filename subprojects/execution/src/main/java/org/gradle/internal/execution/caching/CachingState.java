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

import java.util.Optional;

public interface CachingState {
    /**
     * The cache key if a valid cache key could be built. Might be present even when caching is disabled.
     */
    Optional<BuildCacheKey> getKey();

    /**
     * Reasons for the caching to be disabled for the work, empty when enabled.
     * If empty, {@link #getKey()} is never empty.
     */
    ImmutableList<CachingDisabledReason> getDisabledReasons();

    /**
     * Individual fingerprints for each of the work's inputs.
     */
    Optional<CachingInputs> getInputs();

    CachingState NOT_DETERMINED = disabledWithoutInputs(new CachingDisabledReason(CachingDisabledReasonCategory.UNKNOWN, "Cacheability was not determined"));

    static CachingState disabledWithoutInputs(CachingDisabledReason reason) {
        ImmutableList<CachingDisabledReason> reasons = ImmutableList.of(reason);
        return new CachingState() {
            @Override
            public Optional<BuildCacheKey> getKey() {
                return Optional.empty();
            }

            @Override
            public ImmutableList<CachingDisabledReason> getDisabledReasons() {
                return reasons;
            }

            @Override
            public Optional<CachingInputs> getInputs() {
                return Optional.empty();
            }
        };
    }
}
