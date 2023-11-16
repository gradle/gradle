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

import org.gradle.internal.classpath.GroovyCallInterceptorsProvider
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorRequest

class GroovyInterceptorsSubstitution extends TestInterceptorsSubstitution<CallSiteInterceptorSet> {

    GroovyInterceptorsSubstitution(GroovyCallInterceptorsProvider testProvider) {
        super(new DefaultCallSiteInterceptorSet(testProvider));
    }

    @Override
    ThreadLocalCallSiteInterceptorSet decorateWithThreadLocalImpl(CallSiteInterceptorSet original) {
        return new ThreadLocalCallSiteInterceptorSet(original)
    }

    @Override
    void setCurrentInterceptorSet(CallSiteInterceptorSet newInterceptorSet) {
        CallInterceptorRegistry.GroovyJvmCallInterceptorInternalTesting.currentGroovyCallInterceptorSet = newInterceptorSet
    }

    @Override
    CallSiteInterceptorSet getCurrentInterceptorSet() {
        return CallInterceptorRegistry.GroovyJvmCallInterceptorInternalTesting.currentGroovyCallInterceptorSet;
    }

    private static class ThreadLocalCallSiteInterceptorSet extends ThreadLocalInterceptorSet<CallSiteInterceptorSet> implements CallSiteInterceptorSet {

        ThreadLocalCallSiteInterceptorSet(CallSiteInterceptorSet original) {
            super(original)
        }

        @Override
        List<CallInterceptor> getCallInterceptors(BytecodeInterceptorRequest request) {
            return threadLocalDecorators.get().getCallInterceptors(request)
        }
    }
}
