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
package org.gradle.internal.lazy;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A wrapper around a value computed lazily. Multiple implementations
 * are possible and creating a lazy provider can be done by calling
 * one of the factory methods:
 * <ul>
 *     <li>{@link #unsafe()} would create a lazy wrapper which performs no synchronization at all when calling the supplier: it may be called several times concurrently by different threads. Not thread safe!</li>
 *     <li>{@link #locking()} would create a lazy wrapper which performs locking when calling the supplier: the supplier will only be called once. Reading is done without locking once initialized.</li>
 * </ul>
 *
 * @param <T> the type of the lazy value
 */
public interface Lazy<T> extends Supplier<T> {
    /**
     * Executes an operation on the lazily computed value
     *
     * @param consumer the consumer
     */
    default void use(Consumer<? super T> consumer) {
        consumer.accept(get());
    }

    /**
     * Applies a function to the lazily computed value and returns its value
     *
     * @param function the value to apply to the lazily computed value
     * @param <V> the return type
     * @return the result of the function, applied on the lazily computed value
     */
    default <V> V apply(Function<? super T, V> function) {
        return function.apply(get());
    }

    /**
     * Creates another lazy wrapper which will eventually apply the supplied
     * function to the lazily computed value
     *
     * @param mapper the mapping function
     * @param <V> the type of the result of the function
     * @return a new lazy wrapper
     */
    default <V> Lazy<V> map(Function<? super T, V> mapper) {
        return unsafe().of(() -> mapper.apply(get()));
    }

    /**
     * Constructs a lazy value that always returns the given value.
     *
     * @param <V> the type of the lazy value
     */
    static <V> Lazy<V> fixed(V value) {
        return new FixedLazy<>(value);
    }

    static Factory unsafe() {
        return UnsafeLazy::new;
    }

    /**
     * An atomic {@link Lazy} allows concurrent access from multiple threads without locking.
     * <br>
     * The given {@code Supplier} might be executed more than once when multiple threads access a
     * non-initialized {@link Lazy} at the same time but one value will eventually be cached and
     * published to all threads.
     * <br>
     * <b>WARNING:</b> Given the above, this flavor of {@code Lazy} initialization should not be used
     * with a {@code Supplier} that can have undesirable side effects if executed more than once.
     */
    static Factory atomic() {
        return AtomicLazy::new;
    }

    static Factory locking() {
        return LockingLazy::new;
    }

    interface Factory {
        <T> Lazy<T> of(Supplier<T> supplier);
    }

}
