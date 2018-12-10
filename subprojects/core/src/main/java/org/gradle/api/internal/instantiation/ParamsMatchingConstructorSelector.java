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
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.reflect.JavaReflectionUtil;

import java.lang.reflect.Constructor;
import java.util.List;

public class ParamsMatchingConstructorSelector implements ConstructorSelector {
    private final CrossBuildInMemoryCache<Class<?>, List<Constructor<?>>> constructorCache;
    private final ClassGenerator classGenerator;

    public ParamsMatchingConstructorSelector(ClassGenerator classGenerator, CrossBuildInMemoryCache<Class<?>, List<Constructor<?>>> constructorCache) {
        this.constructorCache = constructorCache;
        this.classGenerator = classGenerator;
    }

    @Override
    public DependencyInjectingInstantiator.CachedConstructor forParams(final Class<?> type, Object[] params) {
        List<Constructor<?>> constructors = constructorCache.get(type, new Transformer<List<Constructor<?>>, Class<?>>() {
            @Override
            public List<Constructor<?>> transform(Class<?> aClass) {
                Class<?> implClass = classGenerator.generate(type);
                ImmutableList.Builder<Constructor<?>> builder = ImmutableList.builder();
                for (Constructor<?> constructor : implClass.getConstructors()) {
                    builder.add(constructor);
                }
                return builder.build();
            }
        });

        Constructor<?> match = null;
        for (Constructor<?> constructor : constructors) {
            Class<?>[] parameterTypes = constructor.getParameterTypes();
            if (parameterTypes.length < params.length) {
                continue;
            }
            int fromParam = 0;
            int toParam = 0;
            for (; fromParam < params.length; fromParam++) {
                while (toParam < parameterTypes.length) {
                    Class<?> toType = parameterTypes[toParam];
                    if (toType.isPrimitive()) {
                        toType = JavaReflectionUtil.getWrapperTypeForPrimitiveType(toType);
                    }
                    if (toType.isInstance(params[fromParam])) {
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
                if (match == null || parameterTypes.length < match.getParameterTypes().length) {
                    // Choose the shortest match
                    match = constructor;
                }
            }
        }
        if (match != null) {
            return  DependencyInjectingInstantiator.CachedConstructor.of(match);
        }
        return DependencyInjectingInstantiator.CachedConstructor.of(new IllegalArgumentException("Could not find constructor for " + type));
    }
}
