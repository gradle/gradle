/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator;

import org.gradle.cache.internal.CrossBuildInMemoryCache;
import org.gradle.internal.Cast;
import org.gradle.internal.logging.text.TreeFormatter;

import java.lang.reflect.Modifier;

class Jsr330ConstructorSelector implements ConstructorSelector {
    private final CrossBuildInMemoryCache<Class<?>, CachedConstructorResult> constructorCache;
    private final ClassGenerator classGenerator;

    public Jsr330ConstructorSelector(ClassGenerator classGenerator, CrossBuildInMemoryCache<Class<?>, CachedConstructorResult> constructorCache) {
        this.constructorCache = constructorCache;
        this.classGenerator = classGenerator;
    }

    @Override
    public void vetoParameters(ClassGenerator.GeneratedConstructor<?> constructor, Object[] parameters) {
        for (Object param : parameters) {
            if (param == null) {
                TreeFormatter formatter = new TreeFormatter();
                formatter.node("Null value provided in parameters ");
                formatter.appendValues(parameters);
                throw new IllegalArgumentException(formatter.toString());
            }
        }
    }

    @Override
    public <T> ClassGenerator.GeneratedConstructor<? extends T> forParams(Class<T> type, Object[] params) {
        return forType(type);
    }

    @Override
    public <T> ClassGenerator.GeneratedConstructor<? extends T> forType(final Class<T> type) throws UnsupportedOperationException {
        // We cache only constructor index, so we don't keep reference to implClass in constructor cache and GC can claim it faster
        ClassGenerator.GeneratedClass<?> implClass = classGenerator.generate(type);
        CachedConstructorResult constructor = constructorCache.get(type, () -> {
            try {
                validateType(type);
                int generatedConstructorIndex = InjectUtil.selectConstructor(implClass, type);
                return CachedConstructorResult.of(generatedConstructorIndex);
            } catch (RuntimeException e) {
                return CachedConstructorResult.of(e);
            }
        });
        return Cast.uncheckedCast(constructor.getConstructor(implClass));
    }

    private static <T> void validateType(Class<T> type) {
        if (!type.isInterface() && type.getEnclosingClass() != null && !Modifier.isStatic(type.getModifiers())) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(type);
            formatter.append(" is a non-static inner class.");
            throw new IllegalArgumentException(formatter.toString());
        }
    }

    public static class CachedConstructorResult {
        private final Integer constructorIndex;
        private final RuntimeException error;

        private CachedConstructorResult(Integer constructorIndex, RuntimeException error) {
            this.constructorIndex = constructorIndex;
            this.error = error;
        }

        public ClassGenerator.GeneratedConstructor<?> getConstructor(ClassGenerator.GeneratedClass<?> implClass) {
            if (error != null) {
                throw error;
            }
            return implClass.getConstructors().get(constructorIndex);
        }

        public static CachedConstructorResult of(Integer ctorIndex) {
            return new CachedConstructorResult(ctorIndex, null);
        }

        public static CachedConstructorResult of(RuntimeException err) {
            return new CachedConstructorResult(null, err);
        }
    }
}
