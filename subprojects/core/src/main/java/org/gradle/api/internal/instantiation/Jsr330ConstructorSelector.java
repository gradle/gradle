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

import org.gradle.api.Transformer;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.InjectUtil;
import org.gradle.cache.internal.CrossBuildInMemoryCache;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class Jsr330ConstructorSelector implements ConstructorSelector {
    private final CrossBuildInMemoryCache<Class<?>, DependencyInjectingInstantiator.CachedConstructor> constructorCache;
    private final ClassGenerator classGenerator;

    public Jsr330ConstructorSelector(ClassGenerator classGenerator, CrossBuildInMemoryCache<Class<?>, DependencyInjectingInstantiator.CachedConstructor> constructorCache) {
        this.constructorCache = constructorCache;
        this.classGenerator = classGenerator;
    }

    @Override
    public DependencyInjectingInstantiator.CachedConstructor forParams(final Class<?> type, Object[] params) {
        return constructorCache.get(type, new Transformer<DependencyInjectingInstantiator.CachedConstructor, Class<?>>() {
            @Override
            public DependencyInjectingInstantiator.CachedConstructor transform(Class<?> aClass) {
                try {
                    validateType(type);
                    Class<?> implClass = classGenerator.generate(type);
                    Constructor<?> constructor = InjectUtil.selectConstructor(implClass, type);
                    constructor.setAccessible(true);
                    return DependencyInjectingInstantiator.CachedConstructor.of(constructor);
                } catch (Throwable e) {
                    return DependencyInjectingInstantiator.CachedConstructor.of(e);
                }
            }
        });
    }

    private static <T> void validateType(Class<T> type) {
        if (type.isInterface() || type.isAnnotation() || type.isEnum()) {
            throw new IllegalArgumentException(String.format("Type %s is not a class.", type.getName()));
        }
        if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is a non-static inner class.", type.getName()));
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            throw new IllegalArgumentException(String.format("Class %s is an abstract class.", type.getName()));
        }
    }
}
