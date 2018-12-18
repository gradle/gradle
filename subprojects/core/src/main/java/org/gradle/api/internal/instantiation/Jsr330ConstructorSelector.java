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
import org.gradle.api.internal.InjectUtil;
import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.text.TreeFormatter;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

public class Jsr330ConstructorSelector implements ConstructorSelector {
    private final CrossBuildInMemoryCache<Class<?>, CachedConstructor> constructorCache;
    private final ClassGenerator classGenerator;

    public Jsr330ConstructorSelector(ClassGenerator classGenerator, CrossBuildInMemoryCache<Class<?>, CachedConstructor> constructorCache) {
        this.constructorCache = constructorCache;
        this.classGenerator = classGenerator;
    }

    @Override
    public SelectedConstructor forParams(final Class<?> type, Object[] params) {
        for (Object param : params) {
            if (param == null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Null value provided in parameters ");
                formatter.appendValues(params);
                throw new IllegalArgumentException(formatter.toString());
            }
        }
        return constructorCache.get(type, new Transformer<CachedConstructor, Class<?>>() {
            @Override
            public CachedConstructor transform(Class<?> aClass) {
                try {
                    validateType(type);
                    Class<?> implClass = classGenerator.generate(type);
                    Constructor<?> constructor = InjectUtil.selectConstructor(implClass, type);
                    constructor.setAccessible(true);
                    return CachedConstructor.of(constructor);
                } catch (Throwable e) {
                    return CachedConstructor.of(e);
                }
            }
        });
    }

    private static <T> void validateType(Class<T> type) {
        if (type.isInterface() || type.isAnnotation() || type.isEnum()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is not a class.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is a non-static inner class.");
            throw new IllegalArgumentException(formatter.toString());
        }
        if (Modifier.isAbstract(type.getModifiers())) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is an abstract class.");
            throw new IllegalArgumentException(formatter.toString());
        }
    }

    public static class CachedConstructor implements SelectedConstructor {
        private final Constructor<?> constructor;
        private final Throwable error;

        private CachedConstructor(Constructor<?> constructor, Throwable error) {
            this.constructor = constructor;
            this.error = error;
        }

        @Override
        public Constructor<?> getConstructor() {
            return constructor;
        }

        @Nullable
        @Override
        public Throwable getFailure() {
            return error;
        }

        public static CachedConstructor of(Constructor<?> ctor) {
            return new CachedConstructor(ctor, null);
        }

        public static CachedConstructor of(Throwable err) {
            return new CachedConstructor(null, err);
        }
    }
}
