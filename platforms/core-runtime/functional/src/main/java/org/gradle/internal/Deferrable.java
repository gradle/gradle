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

package org.gradle.internal;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An invocation to be executed at most once, but one that can be deferred.
 *
 * @param <T> The type which will be computed.
 */
public interface Deferrable<T> {

    /**
     * The result of the invocation when it is already available.
     */
    Optional<T> getCompleted();

    /**
     * Obtain the result of the invocation, either by returning the already computed result or by computing it synchronously.
     *
     * A result is only calculated once.
     */
    T completeAndGet();

    /**
     * Maps the result of the invocation via a mapper.
     *
     * @param mapper An inexpensive function on the result.
     * @throws NullPointerException if the mapper maps to {@code null}.
     */
    default <U> Deferrable<U> map(Function<? super T, U> mapper) {
        return new Deferrable<U>() {
            @Override
            public Optional<U> getCompleted() {
                return Deferrable.this.getCompleted()
                    .map(value -> applyAndRequireNonNull(value, mapper));
            }

            @Override
            public U completeAndGet() {
                return applyAndRequireNonNull(Deferrable.this.completeAndGet(), mapper);
            }

            private U applyAndRequireNonNull(T value, Function<? super T, U> mapper) {
                U result = mapper.apply(value);
                return Objects.requireNonNull(result, "Mapping a Deferrable to null is not allowed");
            }
        };
    }

    /**
     * Chains two {@link Deferrable}s.
     *
     * @param mapper A function which creates the next {@link Deferrable} from the result of the first one.
     * Creating the invocation may be expensive, so this method avoids calling the mapper twice if possible.
     */
    default <U> Deferrable<U> flatMap(Function<? super T, Deferrable<U>> mapper) {
        return getCompleted()
            .map(mapper)
            .orElseGet(() -> Deferrable.deferred(() -> mapper
                .apply(Deferrable.this.completeAndGet())
                .completeAndGet()));
    }

    /**
     * An already completed result, can be successful or failed.
     */
    static <T> Deferrable<T> completed(T successfulResult) {
        return new Deferrable<T>() {
            @Override
            public Optional<T> getCompleted() {
                return Optional.of(successfulResult);
            }

            @Override
            public T completeAndGet() {
                return successfulResult;
            }
        };
    }

    /**
     * An invocation with no pre-computed result, requiring to do the expensive computation on {@link #completeAndGet()}.
     */
    static <T> Deferrable<T> deferred(Supplier<T> result) {
        return new Deferrable<T>() {
            private volatile T value;

            @Override
            public Optional<T> getCompleted() {
                return Optional.ofNullable(value);
            }

            @Override
            public T completeAndGet() {
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
