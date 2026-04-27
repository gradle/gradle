/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.serialization;

import org.gradle.internal.Try;
import org.gradle.internal.evaluation.EvaluationContext;
import org.gradle.internal.evaluation.EvaluationOwner;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Represents a computation that must execute only once and
 * whose result must be cached even (or specially) at serialization time.
 *<p>
 * Instances of this type are mutable and ARE NOT thread-safe,
 * so should not be used from multiple threads.
 *</p>
 * @param <T> the resulting type
 */
public abstract class Cached<T> {

    /**
     * Creates a cacheable computation. The returned object IS NOT safe to be used from multiple threads.
     * If an unresolved cached computation is used from multiple threads, not only it does not honor
     * "at-most-once" semantics, but it can fail in unpredictable ways.
     *
     * @see <a href="https://github.com/gradle/gradle/issues/31239">bug report</a>
     */
    public static <T> Cached<T> of(Callable<T> computation) {
        return new Deferred<>(computation, null);
    }

    /**
     * Creates a cacheable computation that is only evaluated at serialization time when
     * {@code shouldEvaluate} returns {@code true}. When the predicate returns {@code false} the
     * computation is skipped entirely and the cached value is treated as {@code null}.
     *
     * <p>Intended for {@link Cached} fields owned by a task that can be disabled — the predicate
     * is typically {@code task::getEnabled}. A disabled task that will never run pays nothing for
     * its cached input state at configuration cache store time.
     */
    public static <T> Cached<T> of(Callable<T> computation, BooleanSupplier shouldEvaluate) {
        return new Deferred<>(computation, Objects.requireNonNull(shouldEvaluate));
    }

    public abstract T get();

    private static class Deferred<T> extends Cached<T> implements java.io.Serializable, EvaluationOwner {

        // TODO(https://github.com/gradle/gradle/issues/31239) fields are volatile as a workaround for call sites still unwisely using Cached from multiple threads.
        private volatile @Nullable Callable<T> computation;
        private volatile @Nullable BooleanSupplier shouldEvaluate;
        private volatile @Nullable Try<T> result;

        public Deferred(@NonNull Callable<T> computation, @Nullable BooleanSupplier shouldEvaluate) {
            this.computation = computation;
            this.shouldEvaluate = shouldEvaluate;
        }

        @Override
        public T get() {
            return result().get();
        }

        private Try<T> result() {
            Callable<T> toCompute = computation;
            if (result == null) {
                // copy reference into the call stack to avoid exacerbating https://github.com/gradle/gradle/issues/31239
                result = tryComputation(Objects.requireNonNull(toCompute));
                computation = null;
            }
            return result;
        }

        @NonNull
        private Try<T> tryComputation(Callable<T> toCompute) {
            // wrap computation as an "evaluation" so it can be treated specially as other evaluations
            return EvaluationContext.current().evaluate(this, () -> Try.ofFailable(toCompute));
        }

        @SuppressWarnings("NullAway")
        private Object writeReplace() {
            BooleanSupplier check = shouldEvaluate;
            if (check != null && !check.getAsBoolean()) {
                // Predicate vetoed evaluation — don't invoke the computation. The serialized form
                // carries a null result; callers must tolerate get() returning null in this case.
                return new Fixed<T>(Try.successful((T) null));
            }
            return new Fixed<>(result());
        }
    }

    private static class Fixed<T> extends Cached<T> {

        private final Try<T> result;

        public Fixed(Try<T> result) {
            this.result = result;
        }

        @Override
        public T get() {
            return result.get();
        }
    }
}
