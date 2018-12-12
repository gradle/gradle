/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.instantiation;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Transformer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.reflect.JavaReflectionUtil;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.util.List;

public class ParamsMatchingConstructorSelector implements ConstructorSelector {
    private final CrossBuildInMemoryCache<Class<?>, List<CachedConstructor>> constructorCache;
    private final ClassGenerator classGenerator;

    public ParamsMatchingConstructorSelector(ClassGenerator classGenerator, CrossBuildInMemoryCache<Class<?>, List<CachedConstructor>> constructorCache) {
        this.constructorCache = constructorCache;
        this.classGenerator = classGenerator;
    }

    @Override
    public SelectedConstructor forParams(final Class<?> type, Object[] params) {
        List<CachedConstructor> constructors = constructorCache.get(type, new Transformer<List<CachedConstructor>, Class<?>>() {
            @Override
            public List<CachedConstructor> transform(Class<?> aClass) {
                Class<?> implClass = classGenerator.generate(type);
                ImmutableList.Builder<CachedConstructor> builder = ImmutableList.builder();
                for (Constructor<?> constructor : implClass.getConstructors()) {
                    builder.add(new CachedConstructor(constructor));
                }
                return builder.build();
            }
        });

        CachedConstructor match = null;
        for (CachedConstructor constructor : constructors) {
            Class<?>[] parameterTypes = constructor.constructor.getParameterTypes();
            if (parameterTypes.length < params.length) {
                continue;
            }
            int fromParam = 0;
            int toParam = 0;
            for (; fromParam < params.length; fromParam++) {
                Object param = params[fromParam];
                while (param != null && toParam < parameterTypes.length) {
                    Class<?> toType = parameterTypes[toParam];
                    if (toType.isPrimitive()) {
                        toType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(toType);
                    }
                    if (toType.isInstance(param)) {
                        break;
                    }
                    toParam++;
                }
                if (toParam == parameterTypes.length) {
                    break;
                }
                toParam++;
            }
            if (fromParam == params.length) {
                if (match == null || parameterTypes.length < match.constructor.getParameterTypes().length) {
                    // Choose the shortest match
                    match = constructor;
                }
            }
        }
        if (match != null) {
            return match;
        }
        return new BrokenSelection(new IllegalArgumentException("Could not find constructor for " + type));
    }

    public static class CachedConstructor implements SelectedConstructor {
        private final Constructor<?> constructor;

        public CachedConstructor(Constructor<?> constructor) {
            this.constructor = constructor;
        }

        @Override
        public boolean allowsNullParameters() {
            return true;
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return null;
        }

        @Override
        public Constructor<?> getConstructor() {
            return constructor;
        }
    }

    private static class BrokenSelection implements SelectedConstructor {
        private final IllegalArgumentException failure;

        public BrokenSelection(IllegalArgumentException failure) {
            this.failure = failure;
        }

        @Override
        public boolean allowsNullParameters() {
            return true;
        }

        @Override
        public Constructor<?> getConstructor() {
            throw new UnsupportedOperationException();
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return failure;
        }
    }
}
