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

package org.gradle.internal.classpath.intercept

import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorRequest

class JvmInterceptorsSubstitution extends TestInterceptorsSubstitution<JvmBytecodeInterceptorFactorySet> {

    JvmInterceptorsSubstitution(JvmBytecodeInterceptorFactoryProvider testProvider) {
        super(new DefaultJvmBytecodeInterceptorFactorySet(testProvider));
    }

    @Override
    ThreadLocalJvmBytecodeInterceptorFactorySet decorateWithThreadLocalImpl(JvmBytecodeInterceptorFactorySet original) {
        return new ThreadLocalJvmBytecodeInterceptorFactorySet(original)
    }

    @Override
    void setCurrentInterceptorSet(JvmBytecodeInterceptorFactorySet newInterceptorSet) {
        CallInterceptorRegistry.GroovyJvmCallInterceptorInternalTesting.currentJvmBytecodeInterceptorFactorySet = newInterceptorSet
    }

    @Override
    JvmBytecodeInterceptorFactorySet getCurrentInterceptorSet() {
        return CallInterceptorRegistry.GroovyJvmCallInterceptorInternalTesting.currentJvmBytecodeInterceptorFactorySet;
    }

    private static class ThreadLocalJvmBytecodeInterceptorFactorySet extends ThreadLocalInterceptorSet<JvmBytecodeInterceptorFactorySet> implements JvmBytecodeInterceptorFactorySet {

        ThreadLocalJvmBytecodeInterceptorFactorySet(JvmBytecodeInterceptorFactorySet original) {
            super(original)
        }

        @Override
        JvmBytecodeInterceptorSet getJvmBytecodeInterceptorSet(BytecodeInterceptorRequest request) {
            return threadLocalDecorators.get().getJvmBytecodeInterceptorSet(request)
        }
    }
}
