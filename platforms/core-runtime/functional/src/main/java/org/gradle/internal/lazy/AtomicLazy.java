/*
 * Copyright 2023 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @see Lazy#atomic()
 */
class AtomicLazy<T> implements Lazy<T> {

    private static final Object UNINITIALIZED = new Object();

    private final AtomicReference<Object> value = new AtomicReference<>(UNINITIALIZED);

    @Nullable
    private volatile Supplier<T> supplier;

    public AtomicLazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T get() {
        Supplier<T> s = supplier;
        if (s != null) {
            T t = s.get();
            if (value.compareAndSet(UNINITIALIZED, t)) {
                supplier = null;
                return t;
            }
            supplier = null;
        }
        return (T) value.get();
    }
}
