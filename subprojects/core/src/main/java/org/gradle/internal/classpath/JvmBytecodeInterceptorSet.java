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
import org.gradle.api.NonNullApi;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.objectweb.asm.MethodVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.jvm.inspection.JavaInstallationRegistry.distinctByKey;

/**
 * Provides a set of implementation classes for call interception in JVM bytecode, specified by the full class name as in {@link Class#getName()}.
 * The referenced classes should implement {@link org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor}.
 */
@NonNullApi
public interface JvmBytecodeInterceptorSet {
    JvmBytecodeInterceptorSet DEFAULT = new ClassLoaderSourceJvmBytecodeInterceptorSet(JvmBytecodeInterceptorSet.class.getClassLoader());

    List<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData);

    default JvmBytecodeInterceptorSet plus(JvmBytecodeInterceptorSet other) {
        return new CompositeJvmBytecodeInterceptorSet(this, other);
    }

    @NonNullApi
    class ClassLoaderSourceJvmBytecodeInterceptorSet implements JvmBytecodeInterceptorSet {
        private final ClassLoader classLoader;
        private final Predicate<JvmBytecodeCallInterceptor> filter;

        public ClassLoaderSourceJvmBytecodeInterceptorSet(ClassLoader classLoader) {
            this(classLoader, "org.gradle");
        }

        @VisibleForTesting
        public ClassLoaderSourceJvmBytecodeInterceptorSet(ClassLoader classLoader, String forPackage) {
            this.classLoader = classLoader;
            this.filter = callInterceptor -> callInterceptor.getClass().getPackage().getName().startsWith(forPackage);
        }

        @Override
        public List<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData) {
            List<JvmBytecodeCallInterceptor> interceptors = new ArrayList<>();
            for (JvmBytecodeCallInterceptor.Factory factory : ServiceLoader.load(JvmBytecodeCallInterceptor.Factory.class, classLoader)) {
                JvmBytecodeCallInterceptor interceptor = factory.create(methodVisitor, classData);
                if (filter.test(interceptor)) {
                    interceptors.add(interceptor);
                }
            }
            return interceptors;
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
        public List<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData) {
            return Stream.of(first.getInterceptors(methodVisitor, classData), second.getInterceptors(methodVisitor, classData))
                .flatMap(List::stream)
                .filter(distinctByKey(interceptor -> interceptor.getClass().getName()))
                .collect(Collectors.toList());
        }
    }
}

