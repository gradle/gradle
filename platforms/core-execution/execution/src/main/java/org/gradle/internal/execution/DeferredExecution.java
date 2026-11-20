/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.internal.Try;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/// A unit of work served by an identity cache hit, or executed later after a miss.
public final class DeferredExecution<T> {
    private final @Nullable Try<T> cacheHit;
    private final @Nullable Supplier<Try<T>> work;

    private DeferredExecution(@Nullable Try<T> cacheHit, @Nullable Supplier<Try<T>> work) {
        this.cacheHit = cacheHit;
        this.work = work;
    }

    /// Creates an execution served by the given result from the identity cache.
    public static <T> DeferredExecution<T> cacheHit(Try<T> result) {
        return new DeferredExecution<>(result, null);
    }

    /// Creates an execution that missed the identity cache, so executing it runs the given work.
    public static <T> DeferredExecution<T> cacheMiss(Supplier<Try<T>> work) {
        return new DeferredExecution<>(null, work);
    }

    /// The result found in the identity cache, if it was a hit; [#execute()] never sets it.
    public Optional<Try<T>> getCacheHit() {
        return Optional.ofNullable(cacheHit);
    }

    /// Returns the cache hit, or runs the work on every call after a miss.
    public Try<T> execute() {
        Try<T> hit = cacheHit;
        return hit != null ? hit : Objects.requireNonNull(work).get();
    }

    /// Maps a successful result with an inexpensive mapper:
    /// a hit right away, a miss on each execution.
    public <U> DeferredExecution<U> map(Function<? super T, U> mapper) {
        Try<T> hit = cacheHit;
        if (hit != null) {
            return cacheHit(hit.map(mapper));
        }
        Supplier<Try<T>> missed = Objects.requireNonNull(work);
        return new DeferredExecution<>(null, () -> missed.get().map(mapper));
    }

    /// Maps a failed result with an inexpensive mapper:
    /// a hit right away, a miss on each execution.
    public DeferredExecution<T> mapFailure(Function<? super Throwable, ? extends Throwable> mapper) {
        Try<T> hit = cacheHit;
        if (hit != null) {
            return cacheHit(hit.mapFailure(mapper));
        }
        Supplier<Try<T>> missed = Objects.requireNonNull(work);
        return new DeferredExecution<>(null, () -> missed.get().mapFailure(mapper));
    }
}
