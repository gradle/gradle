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

package org.gradle.internal.lazy;

import org.gradle.internal.Cast;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

/**
 * @see Lazy#map(Function)
 */
class MappedLazy<T extends @Nullable Object, V extends @Nullable Object> implements Lazy<V> {
    private static final Object UNINITIALIZED = new Object();

    // The type is as nullable as V, but it cannot be expressed naturally in Java.
    private volatile @Nullable Object value = UNINITIALIZED;
    private volatile @Nullable Lazy<T> source;
    // `mapper` is only read under synchronization, so it doesn't need to be volatile
    private @Nullable Function<? super T, V> mapper;

    MappedLazy(Lazy<T> source, Function<? super T, V> mapper) {
        this.mapper = mapper;
        this.source = source;
    }

    @Override
    public boolean isInitialized() {
        if (value != UNINITIALIZED) {
            return true;
        }
        Lazy<T> currentSource = source;
        return currentSource == null || currentSource.isInitialized();
    }

    boolean hasMappedValue() {
        return value != UNINITIALIZED;
    }

    @Override
    public V get() {
        Object current = value;
        if (current == UNINITIALIZED) {
            Lazy<T> currentSource = source;
            // Value is published before the source is cleared, so if it is gone then the value is already available.
            if (currentSource != null) {
                // Compute the source outside the monitor so we're not holding the lock over it.
                T input = currentSource.get();
                synchronized (this) {
                    if (value == UNINITIALIZED) {
                        Function<? super T, V> currentMapper = Objects.requireNonNull(mapper);
                        value = currentMapper.apply(input);
                        source = null;
                        mapper = null;
                    }
                }
            }
            current = value;
        }
        // value now holds a valid value of type V from the mapper, so we can strip unconditional nullability from it.
        return Cast.unsafeStripNullable(Cast.uncheckedCast(current));
    }
}
