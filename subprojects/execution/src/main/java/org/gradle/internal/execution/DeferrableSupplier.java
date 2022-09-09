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
public interface DeferrableSupplier<T> {

    /**
     * The result of the invocation when it is already available.
     */
    Optional<Try<T>> completed();

    /**
     * Obtain the result of the invocation, either by returning the already computed result or by computing it synchronously.
     *
     * A result is only calculated once.
     */
    Try<T> completeAndGet();

    /**
     * Maps the result of the invocation via a mapper.
     *
     * @param mapper An inexpensive function on the result.
     */
    default <U> DeferrableSupplier<U> map(Function<? super T, U> mapper) {
        return new DeferrableSupplier<U>() {
            @Override
            public Optional<Try<U>> completed() {
                return DeferrableSupplier.this.completed()
                    .map(result -> result.map(mapper));
            }

            @Override
            public Try<U> completeAndGet() {
                return DeferrableSupplier.this.completeAndGet()
                    .map(mapper);
            }
        };
    }

    default DeferrableSupplier<T> mapFailure(Function<? super Throwable, ? extends Throwable> mapper) {
        return new DeferrableSupplier<T>() {
            @Override
            public Optional<Try<T>> completed() {
                return DeferrableSupplier.this.completed()
                    .map(result -> result.mapFailure(mapper));
            }

            @Override
            public Try<T> completeAndGet() {
                return DeferrableSupplier.this.completeAndGet()
                    .mapFailure(mapper);
            }
        };
    }

    /**
     * Chains two {@link DeferrableSupplier}s.
     *
     * @param mapper A function which creates the next {@link DeferrableSupplier} from the result of the first one.
     * Creating the invocation may be expensive, so this method avoids calling the mapper twice if possible.
     */
    default <U> DeferrableSupplier<U> flatMap(Function<? super T, DeferrableSupplier<U>> mapper) {
        return completed()
            .map(cachedResult -> cachedResult
                .tryMap(mapper)
                .getOrMapFailure(DeferrableSupplier::failed))
            .orElseGet(() -> deferred(() -> completeAndGet()
                .flatMap(intermediateResult -> mapper.apply(intermediateResult)
                    .completeAndGet())));
    }

    /**
     * An already completed result, can be successful or failed.
     */
    static <T> DeferrableSupplier<T> completed(Try<T> successfulResult) {
        return new DeferrableSupplier<T>() {
            @Override
            public Optional<Try<T>> completed() {
                return Optional.of(successfulResult);
            }

            @Override
            public Try<T> completeAndGet() {
                return successfulResult;
            }
        };
    }

    /**
     * A successfully completed result.
     */
    static <T> DeferrableSupplier<T> successful(T result) {
        return completed(Try.successful(result));
    }

    /**
     * A failed result.
     */
    static <T> DeferrableSupplier<T> failed(Throwable failure) {
        return completed(Try.failure(failure));
    }

    /**
     * An invocation with no pre-computed result, requiring to do the expensive computation on {@link #get}.
     */
    static <T> DeferrableSupplier<T> deferred(Supplier<Try<T>> result) {
        return new DeferrableSupplier<T>() {
            private transient volatile Try<T> value;

            @Override
            public Optional<Try<T>> completed() {
                return Optional.ofNullable(value);
            }

            @Override
            public Try<T> completeAndGet() {
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
