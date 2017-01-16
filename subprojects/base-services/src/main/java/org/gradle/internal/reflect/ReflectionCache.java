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

import java.util.WeakHashMap;

/**
 * A generic purpose, thread-safe cache which is aimed at storing information
 * about a class. The key is a weak-reference to the class, and it is the responsibility
 * of the subclasses to make sure that the values stored in the cache do not prevent
 * the class itself from being collected.
 *
 * @param <T> the type of the element stored in the cache.
 */
public abstract class ReflectionCache<T> {
    private final Object lock = new Object();
    private final WeakHashMap<Class<?>, T> cache = new WeakHashMap<Class<?>, T>();

    public T get(Class<?> key) {
        T cached;
        synchronized (lock) {
            cached = cache.get(key);
        }
        if (cached != null) {
            return cached;
        }
        return getAndCache(key);
    }

    protected abstract T create(Class<?> key);

    public int size() {
        return cache.size();
    }

    private T getAndCache(Class<?> key) {
        T created = create(key);
        synchronized (lock) {
            cache.put(key, created);
        }
        return created;
    }

}
