/*
 * Copyright 2022 the original author or authors.
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

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An invocation that can potentially be deferred.
 *
 * @param <T> The type which will be computed.
 */
public interface DeferrableExecution<T> {

    /**
     * The result of the invocation when it is already available.
     */
    Optional<Try<T>> getCompleted();

    /**
     * Obtain the result of the invocation, either by returning the already known result or by computing it.
     */
    Try<T> get();

    /**
     * Maps the result of the invocation via a mapper.
     *
     * @param mapper An inexpensive function on the result.
     */
    default <U> DeferrableExecution<U> map(Function<? super T, U> mapper) {
        return new DeferrableExecution<U>() {
            @Override
            public Optional<Try<U>> getCompleted() {
                return DeferrableExecution.this.getCompleted()
                    .map(result -> result.map(mapper));
            }

            @Override
            public Try<U> get() {
                return DeferrableExecution.this.get()
                    .map(mapper);
            }
        };
    }

    default DeferrableExecution<T> mapFailure(Function<? super Throwable, ? extends Throwable> mapper) {
        return new DeferrableExecution<T>() {
            @Override
            public Optional<Try<T>> getCompleted() {
                return DeferrableExecution.this.getCompleted()
                    .map(result -> result.mapFailure(mapper));
            }

            @Override
            public Try<T> get() {
                return DeferrableExecution.this.get()
                    .mapFailure(mapper);
            }
        };
    }

    /**
     * Chains two {@link DeferrableExecution}s.
     *
     * @param mapper A function which creates the next {@link DeferrableExecution} from the result of the first one.
     * Creating the invocation may be expensive, so this method avoids calling the mapper twice if possible.
     */
    default <U> DeferrableExecution<U> flatMap(Function<? super T, DeferrableExecution<U>> mapper) {
        return getCompleted()
            .map(cachedResult -> cachedResult
                .tryMap(mapper)
                .getOrMapFailure(DeferrableExecution::failed))
            .orElseGet(() -> deferred(() -> get()
                .flatMap(intermediateResult -> mapper.apply(intermediateResult).get())));
    }

    /**
     * An already completed result, can be successful or failed.
     */
    static <T> DeferrableExecution<T> completed(Try<T> successfulResult) {
        return new DeferrableExecution<T>() {
            @Override
            public Optional<Try<T>> getCompleted() {
                return Optional.of(successfulResult);
            }

            @Override
            public Try<T> get() {
                return successfulResult;
            }
        };
    }

    /**
     * An already successful result.
     */
    static <T> DeferrableExecution<T> successful(T result) {
        return completed(Try.successful(result));
    }

    /**
     * An already failed result.
     */
    static <T> DeferrableExecution<T> failed(Throwable failure) {
        return completed(Try.failure(failure));
    }

    /**
     * An invocation with no pre-computed result, requiring to do the expensive computation on {@link #get}.
     */
    static <T> DeferrableExecution<T> deferred(Supplier<Try<T>> result) {
        return new DeferrableExecution<T>() {
            private transient volatile Try<T> value;

            @Override
            public Optional<Try<T>> getCompleted() {
                return Optional.ofNullable(value);
            }

            @Override
            public Try<T> get() {
                if (value == null) {
                    synchronized (this) {
                        if (value == null) {
                            value = result.get();
                        }
                    }
                }
                return value;
            }
        };
    }
}
