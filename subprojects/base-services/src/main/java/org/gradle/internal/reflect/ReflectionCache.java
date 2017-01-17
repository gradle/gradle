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

/**
 * A generic purpose, thread-safe cache which is aimed at storing information
 * about a class. The cache is a hierachical cache, which key is a composite
 * of a receiver, and argument types. All those, key or arguments, are kept
 * in a weak reference, allowing the GC to recover memory if required.
 *
 * @param <T> the type of the element stored in the cache.
 */
public abstract class ReflectionCache<T extends ReflectionCache.CachedInvokable<?>> {
    private final Object lock = new Object();

    private final WeaklyClassReferencingCache cache = new WeaklyClassReferencingCache();

    public T get(final Class<?> receiver, final Class<?>[] key) {
        synchronized (lock) {
            return cache.get(receiver, key);
        }
    }

    protected abstract T create(Class<?> receiver, Class<?>[] key);

    public int size() {
        return cache.size();
    }

    private class WeaklyClassReferencingCache extends WeakHashMap<Class<?>, CacheEntry> {

        public T get(Class<?> receiver, Class<?>[] classes) {
            WeaklyClassReferencingCache cur = this;
            CacheEntry last = fetchNext(cur, receiver);
            for (Class<?> aClass : classes) {
                cur = last.table;
                last = fetchNext(cur, aClass);
            }
            if (last.value == null || last.value.getMethod() == null) {
                last.value = create(receiver, classes);
            }
            return last.value;
        }

        private CacheEntry fetchNext(WeaklyClassReferencingCache cur, Class<?> aClass) {
            CacheEntry last;
            last = cur.get(aClass);
            if (last == null) {
                last = new CacheEntry();
                cur.put(aClass, last);
            }
            return last;
        }
    }

    private class CacheEntry {
        private WeaklyClassReferencingCache table = new WeaklyClassReferencingCache();
        private T value;
    }

    public static class CachedInvokable<T> {
        private final WeakReference<T> invokable;

        public CachedInvokable(T invokable) {
            this.invokable = new WeakReference<T>(invokable);
        }

        public T getMethod() {
            return invokable.get();
        }
    }
}
