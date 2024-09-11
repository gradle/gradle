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
import org.gradle.internal.execution.history.BeforeExecutionState;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class CachingState {
    public static final CachingState NOT_DETERMINED = disabledWithoutInputs(new CachingDisabledReason(CachingDisabledReasonCategory.UNKNOWN, "Cacheability was not determined"));

    private final Either<Enabled, Disabled> delegate;

    private CachingState(Either<Enabled, Disabled> delegate) {
        this.delegate = delegate;
    }

    public static CachingState enabled(BuildCacheKey key, BeforeExecutionState beforeExecutionState) {
        return new CachingState(Either.left(new Enabled(key, beforeExecutionState)));
    }

    public static CachingState disabled(ImmutableList<CachingDisabledReason> disabledReasons, @Nullable BuildCacheKey key, @Nullable BeforeExecutionState beforeExecutionState) {
        CacheKeyCalculatedState cacheKeyCalculatedState = (key != null && beforeExecutionState != null)
            ? new CacheKeyCalculatedState(key, beforeExecutionState)
            : null;
        return new CachingState(Either.right(new Disabled(disabledReasons, cacheKeyCalculatedState)));
    }

    public static CachingState disabledWithoutInputs(CachingDisabledReason reason) {
        return disabled(ImmutableList.of(reason), null, null);
    }

    public Optional<Enabled> whenEnabled() {
        return delegate.getLeft();
    }

    public Optional<Disabled> whenDisabled() {
        return delegate.getRight();
    }

    public <T> T fold(Function<Enabled, T> enabled, Function<Disabled, T> disabled) {
        return delegate.fold(enabled, disabled);
    }

    public void apply(Consumer<Enabled> enabled, Consumer<Disabled> disabled) {
        delegate.apply(enabled, disabled);
    }

    public Optional<CacheKeyCalculatedState> getCacheKeyCalculatedState() {
        return delegate.fold(enabled -> Optional.of(enabled.getCacheKeyCalculatedState()), Disabled::getCacheKeyCalculatedState);
    }

    /**
     * State when we calculated the cache key.
     *
     * We always calculate the cache key when we have a {@link BeforeExecutionState}.
     * In some places we need to know that both are present or absent, we encode this here.
     */
    public static class CacheKeyCalculatedState {
        private final BuildCacheKey key;
        private final BeforeExecutionState beforeExecutionState;

        private CacheKeyCalculatedState(BuildCacheKey key, BeforeExecutionState beforeExecutionState) {
            this.key = key;
            this.beforeExecutionState = beforeExecutionState;
        }

        /**
         * The cache key.
         */
        public BuildCacheKey getKey() {
            return key;
        }

        /**
         * The captured state of the work's inputs.
         */
        public BeforeExecutionState getBeforeExecutionState() {
            return beforeExecutionState;
        }
    }

    /**
     * Caching state when caching is enabled for the work.
     */
    public static class Enabled {
        private final CacheKeyCalculatedState cacheKeyCalculatedState;

        private Enabled(BuildCacheKey key, BeforeExecutionState beforeExecutionState) {
            this.cacheKeyCalculatedState = new CacheKeyCalculatedState(key, beforeExecutionState);
        }

        public CacheKeyCalculatedState getCacheKeyCalculatedState() {
            return cacheKeyCalculatedState;
        }
    }

    /**
     * Caching state when caching is disabled for the work.
     */
    public static class Disabled {
        private final ImmutableList<CachingDisabledReason> disabledReasons;
        private final CacheKeyCalculatedState cacheKeyCalculatedState;

        private Disabled(ImmutableList<CachingDisabledReason> disabledReasons, @Nullable CacheKeyCalculatedState cacheKeyCalculatedState) {
            this.disabledReasons = disabledReasons;
            this.cacheKeyCalculatedState = cacheKeyCalculatedState;
        }

        /**
         * Reasons for the caching to be disabled for the work, empty when enabled.
         */
        public ImmutableList<CachingDisabledReason> getDisabledReasons() {
            return disabledReasons;
        }

        /**
         * The cache key calculated state.
         *
         * Not available when we don't capture the before execution state.
         */
        public Optional<CacheKeyCalculatedState> getCacheKeyCalculatedState() {
            return Optional.ofNullable(cacheKeyCalculatedState);
        }
    }
}
