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

import org.gradle.internal.classpath.ClassData;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.instrumentation.api.jvmbytecode.JvmBytecodeCallInterceptor;
import org.objectweb.asm.MethodVisitor;

import java.util.List;
import java.util.stream.Collectors;

public class DefaultJvmBytecodeInterceptorFactorySet implements JvmBytecodeInterceptorFactorySet {

    private final JvmBytecodeInterceptorFactoryProvider provider;

    public DefaultJvmBytecodeInterceptorFactorySet(JvmBytecodeInterceptorFactoryProvider provider) {
        this.provider = provider;
    }

    @Override
    public JvmBytecodeInterceptorSet getJvmBytecodeInterceptorSet(BytecodeInterceptorFilter filter) {
        List<JvmBytecodeCallInterceptor.Factory> factories = provider.getInterceptorFactories().stream()
            .filter(filter::matches)
            .collect(Collectors.toList());
        return new JvmBytecodeInterceptorSet() {
            @Override
            public List<JvmBytecodeCallInterceptor> getInterceptors(MethodVisitor methodVisitor, ClassData classData) {
                return factories.stream()
                    .map(factory -> factory.create(methodVisitor, classData, filter))
                    .collect(Collectors.toList());
            }

            @Override
            public BytecodeInterceptorFilter getOriginalFilter() {
                return filter;
            }
        };
    }
}
