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
package org.gradle.internal.reflect;

import com.google.common.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.WeakHashMap;

public class DirectInstantiator implements Instantiator {

    public static final Instantiator INSTANCE = new DirectInstantiator();

    private final ConstructorCache constructorCache = new ConstructorCache();

    public static <T> T instantiate(Class<? extends T> type, Object... params) {
        return INSTANCE.newInstance(type, params);
    }

    private DirectInstantiator() {
    }

    public <T> T newInstance(Class<? extends T> type, Object... params) {
        try {
            Constructor<?> match = doGetConstructor(type, constructorCache.get(type), params);
            return type.cast(match.newInstance(params));
        } catch (InvocationTargetException e) {
            throw new ObjectInstantiationException(type, e.getCause());
        } catch (Exception e) {
            throw new ObjectInstantiationException(type, e);
        }
    }

    private <T> Constructor<?> doGetConstructor(Class<? extends T> type, CachedConstructor[] constructors, Object[] params) {
        CachedConstructor match = null;
        if (constructors.length > 0) {
            for (CachedConstructor constructor : constructors) {
                if (constructor.isMatch(params)) {
                    if (match != null) {
                        throw new IllegalArgumentException(String.format("Found multiple public constructors for %s which accept parameters %s.", type, Arrays.toString(params)));
                    }
                    match = constructor;
                }
            }
        }
        if (match == null) {
            throw new IllegalArgumentException(String.format("Could not find any public constructor for %s which accepts parameters %s.", type, Arrays.toString(params)));
        }
        return match.constructor.get();
    }

    private static class CachedConstructor {
        private final WeakReference<Constructor<?>> constructor;
        private final Class<?>[] parameterTypes;
        private final int paramCount;
        private final boolean[] isPrimitive;
        private final Class<?>[] wrappedParameterTypes;

        public CachedConstructor(Constructor<?> ctor) {
            this.constructor = new WeakReference<Constructor<?>>(ctor);
            this.parameterTypes = ctor.getParameterTypes();
            this.paramCount = this.parameterTypes.length;
            this.isPrimitive = new boolean[paramCount];
            this.wrappedParameterTypes = new Class[paramCount];
            for (int i = 0; i < paramCount; i++) {
                Class<?> parameterType = parameterTypes[i];
                boolean primitive = parameterType.isPrimitive();
                this.isPrimitive[i] = primitive;
                this.wrappedParameterTypes[i] = primitive ? JavaReflectionUtil.getWrapperTypeForPrimitiveType(parameterType) : parameterType;
            }
        }

        private boolean isMatch(Object... params) {
            if (paramCount != params.length) {
                return false;
            }
            for (int i = 0; i < paramCount; i++) {
                Object param = params[i];
                Class<?> parameterType = parameterTypes[i];
                if (isPrimitive[i]) {
                    if (!wrappedParameterTypes[i].isInstance(param)) {
                        return false;
                    }
                } else {
                    if (param != null && !parameterType.isInstance(param)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    @VisibleForTesting
    public static class ConstructorCache {
        private final Object lock = new Object();
        private final WeakHashMap<Class<?>, WeakReference<CachedConstructor[]>> cache = new WeakHashMap<Class<?>, WeakReference<CachedConstructor[]>>();

        public CachedConstructor[] get(Class<?> key) {
            WeakReference<CachedConstructor[]> cached;
            synchronized (lock) {
                cached = cache.get(key);
            }
            if (cached != null) {
                CachedConstructor[] ctrs = cached.get();
                if (ctrs != null) {
                    return ctrs;
                }
            }
            return getAndCache(key);
        }

        private CachedConstructor[] getAndCache(Class<?> key) {
            CachedConstructor[] ctors = cache(key.getConstructors());
            WeakReference<CachedConstructor[]> value = new WeakReference<CachedConstructor[]>(ctors);
            synchronized (lock) {
                cache.put(key, value);
            }
            return ctors;
        }

        private CachedConstructor[] cache(Constructor<?>[] constructors) {
            CachedConstructor[] cachedConstructors = new CachedConstructor[constructors.length];
            for (int i = 0; i < constructors.length; i++) {
                cachedConstructors[i] = new CachedConstructor(constructors[i]);
            }
            return cachedConstructors;
        }
    }
}
