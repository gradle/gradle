/*
 * Copyright 2012 the original author or authors.
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

import java.lang.ref.SoftReference;
import java.util.concurrent.atomic.AtomicReference;

public final class Factories {

    private Factories() {
        /* no-op */
    }

    public static <T> Factory<T> toFactory(final Runnable runnable) {
        return new Factory<T>() {
            @Override
            public T create() {
                runnable.run();
                return null;
            }
        };
    }

    public static <T> Factory<T> constant(final T item) {
        return new Factory<T>() {
            @Override
            public T create() {
                return item;
            }
        };
    }

    public static <T> Factory<T> softReferenceCache(Factory<T> factory) {
        return new CachingSoftReferenceFactory<T>(factory);
    }

    private static class CachingSoftReferenceFactory<T> implements Factory<T> {
        private final Factory<T> factory;
        private final AtomicReference<SoftReference<T>> cachedReference = new AtomicReference<SoftReference<T>>();

        public CachingSoftReferenceFactory(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        public T create() {
            SoftReference<T> reference = cachedReference.get();
            T value = reference != null ? reference.get() : null;
            if (value == null) {
                value = factory.create();
                cachedReference.set(new SoftReference<T>(value));
            }
            return value;
        }
    }
}
