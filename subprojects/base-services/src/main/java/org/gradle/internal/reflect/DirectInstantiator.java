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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

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

    private <T> Constructor<?> doGetConstructor(Class<? extends T> type, JavaReflectionUtil.CachedConstructor[] constructors, Object[] params) {
        JavaReflectionUtil.CachedConstructor match = null;
        if (constructors.length > 0) {
            for (JavaReflectionUtil.CachedConstructor constructor : constructors) {
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
        return match.getConstructor();
    }

    @VisibleForTesting
    public static class ConstructorCache extends ReflectionCache<JavaReflectionUtil.CachedConstructor[]> {
        @Override
        protected boolean hasExpired(JavaReflectionUtil.CachedConstructor[] cached) {
            for (JavaReflectionUtil.CachedConstructor cachedConstructor : cached) {
                if (hasExpired(cachedConstructor)) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasExpired(JavaReflectionUtil.CachedConstructor cachedConstructor) {
            return cachedConstructor.hasExpired();
        }

        @Override
        protected JavaReflectionUtil.CachedConstructor[] create(Class<?> key) {
            Constructor<?>[] constructors = key.getConstructors();
            JavaReflectionUtil.CachedConstructor[] cachedConstructors = new JavaReflectionUtil.CachedConstructor[constructors.length];
            for (int i = 0; i < constructors.length; i++) {
                cachedConstructors[i] = new JavaReflectionUtil.CachedConstructor(constructors[i]);
            }
            return cachedConstructors;
        }
    }
}
