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

import org.gradle.api.NonNullApi;
import org.gradle.internal.instrumentation.api.declarations.InterceptorDeclaration;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a set of implementation classes for call interception in JVM bytecode, specified by the full class name as in {@link Class#getName()}.
 * The referenced classes should implement {@link org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor}.
 */
@NonNullApi
public interface JvmBytecodeInterceptorSet {
    JvmBytecodeInterceptorSet DEFAULT = () -> InterceptorDeclaration.JVM_BYTECODE_GENERATED_CLASS_NAMES;

    List<String> interceptorClassNames();

    default JvmBytecodeInterceptorSet plus(JvmBytecodeInterceptorSet other) {
        return () -> Stream.of(this, other).flatMap(it -> it.interceptorClassNames().stream()).distinct().collect(Collectors.toList());
    }
}

