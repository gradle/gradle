/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.lazy.Lazy;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a set of implementation classes for call interception in JVM bytecode, specified by the full class name as in {@link Class#getName()}.
 * The referenced classes should implement {@link org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor}.
 */
@NonNullApi
public interface JvmBytecodeInterceptorFactoryProvider {

    @SuppressWarnings("ClassInitializationDeadlock")
    JvmBytecodeInterceptorFactoryProvider DEFAULT = new ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(JvmBytecodeInterceptorFactoryProvider.class.getClassLoader());

    List<JvmBytecodeCallInterceptor.Factory> getInterceptorFactories();

    default JvmBytecodeInterceptorFactoryProvider plus(JvmBytecodeInterceptorFactoryProvider other) {
        return new CompositeJvmBytecodeInterceptorFactoryProvider(this, other);
    }

    @NonNullApi
    class ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider implements JvmBytecodeInterceptorFactoryProvider {

        private final Lazy<List<JvmBytecodeCallInterceptor.Factory>> factories;

        public ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(ClassLoader classLoader) {
            this(classLoader, "");
        }

        @VisibleForTesting
        public ClassLoaderSourceJvmBytecodeInterceptorFactoryProvider(ClassLoader classLoader, String forPackage) {
            this.factories = Lazy.locking().of(() -> loadFactories(classLoader, forPackage));
        }

        private static List<JvmBytecodeCallInterceptor.Factory> loadFactories(ClassLoader classLoader, String forPackage) {
            ImmutableList.Builder<JvmBytecodeCallInterceptor.Factory> factories = ImmutableList.builder();
            for (JvmBytecodeCallInterceptor.Factory factory : ServiceLoader.load(JvmBytecodeCallInterceptor.Factory.class, classLoader)) {
                if (factory.getClass().getPackage().getName().startsWith(forPackage)) {
                    factories.add(factory);
                }
            }
            return factories.build();
        }

        @Override
        public List<JvmBytecodeCallInterceptor.Factory> getInterceptorFactories() {
            return factories.get();
        }
    }

    @NonNullApi
    class CompositeJvmBytecodeInterceptorFactoryProvider implements JvmBytecodeInterceptorFactoryProvider {
        private final JvmBytecodeInterceptorFactoryProvider first;
        private final JvmBytecodeInterceptorFactoryProvider second;

        public CompositeJvmBytecodeInterceptorFactoryProvider(JvmBytecodeInterceptorFactoryProvider first, JvmBytecodeInterceptorFactoryProvider second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public List<JvmBytecodeCallInterceptor.Factory> getInterceptorFactories() {
            return ImmutableList.copyOf(Stream.of(first.getInterceptorFactories(), second.getInterceptorFactories())
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(interceptor -> interceptor.getClass().getName(), p -> p, (p, q) -> p, LinkedHashMap::new))
                .values());
        }
    }
}

