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

package org.gradle.internal.classpath;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.NonNullApi;
import org.gradle.internal.instrumentation.api.capabilities.BytecodeUpgradeInterceptor;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.gradle.internal.lazy.Lazy;
import org.objectweb.asm.MethodVisitor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a set of implementation classes for call interception in JVM bytecode, specified by the full class name as in {@link Class#getName()}.
 * The referenced classes should implement {@link org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor}.
 */
@NonNullApi
public interface JvmBytecodeInterceptorSet {

    /**
     * Default set of interceptors, but without any bytecode interceptor, since we want to be able to filter them.
     */

    JvmBytecodeInterceptorSet DEFAULT = new ClassLoaderSourceJvmBytecodeInterceptorSet(JvmBytecodeInterceptorSet.class.getClassLoader(), type -> !BytecodeUpgradeInterceptor.class.isAssignableFrom(type));

    Collection<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData);

    default JvmBytecodeInterceptorSet plus(JvmBytecodeInterceptorSet other) {
        return new CompositeJvmBytecodeInterceptorSet(this, other);
    }

    @NonNullApi
    class ClassLoaderSourceJvmBytecodeInterceptorSet implements JvmBytecodeInterceptorSet {

        private final Lazy<List<JvmBytecodeCallInterceptor.Factory>> factories;

        public ClassLoaderSourceJvmBytecodeInterceptorSet(ClassLoader classLoader, Predicate<Class<?>> interceptorTypePredicate) {
            this.factories = Lazy.locking().of(() -> loadFactories(classLoader, interceptorTypePredicate));
        }

        @VisibleForTesting
        public ClassLoaderSourceJvmBytecodeInterceptorSet(ClassLoader classLoader, String forPackage) {
            this(classLoader, type -> type.getPackage().getName().startsWith(forPackage));
        }

        private static List<JvmBytecodeCallInterceptor.Factory> loadFactories(ClassLoader classLoader, Predicate<Class<?>> interceptorTypePredicate) {
            ImmutableList.Builder<JvmBytecodeCallInterceptor.Factory> factories = ImmutableList.builder();
            for (JvmBytecodeCallInterceptor.Factory factory : ServiceLoader.load(JvmBytecodeCallInterceptor.Factory.class, classLoader)) {
                if (interceptorTypePredicate.test(factory.getInterceptorType())) {
                    factories.add(factory);
                }
            }
            return factories.build();
        }

        @Override
        public Collection<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData) {
            ImmutableList.Builder<JvmBytecodeCallInterceptor> interceptors = ImmutableList.builder();
            for (JvmBytecodeCallInterceptor.Factory factory : factories.get()) {
                interceptors.add(factory.create(methodVisitor, classData));
            }
            return interceptors.build();
        }
    }

    @NonNullApi
    class CompositeJvmBytecodeInterceptorSet implements JvmBytecodeInterceptorSet {
        private final JvmBytecodeInterceptorSet first;
        private final JvmBytecodeInterceptorSet second;

        public CompositeJvmBytecodeInterceptorSet(JvmBytecodeInterceptorSet first, JvmBytecodeInterceptorSet second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public Collection<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData) {
            return Stream.of(first.getInterceptors(methodVisitor, classData), second.getInterceptors(methodVisitor, classData))
                .flatMap(Collection::stream)
                .collect(Collectors.toMap(interceptor -> interceptor.getClass().getName(), p -> p, (p, q) -> p, LinkedHashMap::new))
                .values();
        }
    }
}
