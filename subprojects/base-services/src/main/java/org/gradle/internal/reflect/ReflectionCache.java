/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.internal.reflect;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public abstract class ReflectionCache<T> {
    private final Object lock = new Object();
    private final WeakHashMap<Class<?>, WeakReference<T>> cache = new WeakHashMap<Class<?>, WeakReference<T>>();

    public T get(Class<?> key) {
        WeakReference<T> cached;
        synchronized (lock) {
            cached = cache.get(key);
        }
        if (cached != null) {
            T ctrs = cached.get();
            if (ctrs != null) {
                return ctrs;
            }
        }
        return getAndCache(key);
    }

    protected abstract T create(Class<?> key);

    public int size() {
        return cache.size();
    }

    private T getAndCache(Class<?> key) {
        T created = create(key);
        WeakReference<T> value = new WeakReference<T>(created);
        synchronized (lock) {
            cache.put(key, value);
        }
        return created;
    }

}
