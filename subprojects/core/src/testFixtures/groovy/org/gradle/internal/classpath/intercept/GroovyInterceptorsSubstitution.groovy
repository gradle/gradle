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
import org.gradle.internal.classpath.Instrumented
import org.gradle.internal.instrumentation.api.capabilities.InterceptorsRequest

import javax.annotation.Nullable
import java.util.concurrent.atomic.AtomicInteger

/**
 * Provides support for replacing the Groovy interceptors for the current thread.
 * Make sure that the call sites that should be affected by the change are only reached after the interceptors have been substituted.
 *
 * The interceptors are substituted per-thread because other threads may be executing unrelated tests.
 */
class GroovyInterceptorsSubstitution {
    /**
     * Ensures that the global Groovy call site decorator is replaced with an implementation that maintains call site decorators per thread.
     * Then sets up the call site decorator for the current thread only.
     */
    void setupForCurrentThread() {
        synchronized (Instrumented.class) {
            maybeSetGlobalCallSiteDecorator()
                .substituteForCurrentThread(
                    new DefaultGroovyCallSiteInterceptorSet(substitutionProvider)
                )
        }
    }

    /**
     * Cancels the Groovy call site decorator substitution for the current thread.
     * If the global implementation does not have any other active callsite decorators in the other threads, reverts the global call site
     * decorator implementation.
     */
    static void cleanupForCurrentThread() {
        synchronized (Instrumented.class) {
            substitutionIfPresent()?.cancelSubstitutionForCurrentThread()
            maybeRevertGlobalCallSiteDecorator()
        }
    }

    private final GroovyCallInterceptorsProvider substitutionProvider

    GroovyInterceptorsSubstitution(GroovyCallInterceptorsProvider substitutionProvider) {
        this.substitutionProvider = substitutionProvider
    }

    @Nullable
    private static ThreadLocalGroovyCallInterceptorSetDecorator substitutionIfPresent() {
        def decorator = CallInterceptorRegistry.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator instanceof ThreadLocalGroovyCallInterceptorSetDecorator) {
            return decorator as ThreadLocalGroovyCallInterceptorSetDecorator
        }
        return null
    }

    private static ThreadLocalGroovyCallInterceptorSetDecorator maybeSetGlobalCallSiteDecorator() {
        def decorator = CallInterceptorRegistry.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator !instanceof ThreadLocalGroovyCallInterceptorSetDecorator) {
            decorator = new ThreadLocalGroovyCallInterceptorSetDecorator(decorator)
            CallInterceptorRegistry.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator = decorator
        }
        return decorator as ThreadLocalGroovyCallInterceptorSetDecorator
    }

    private static void maybeRevertGlobalCallSiteDecorator() {
        def decorator = CallInterceptorRegistry.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator instanceof ThreadLocalGroovyCallInterceptorSetDecorator) {
            if (decorator.isEmpty()) {
                CallInterceptorRegistry.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator = decorator.original
            }
        }
    }

    private static class ThreadLocalGroovyCallInterceptorSetDecorator implements GroovyCallSiteInterceptorSet {

        public final GroovyCallSiteInterceptorSet original

        private final AtomicInteger substitutions = new AtomicInteger(0)
        private final ThreadLocal<GroovyCallSiteInterceptorSet> threadLocalDecorators = ThreadLocal.withInitial { original }

        boolean isEmpty() {
            substitutions.get() == 0
        }

        ThreadLocalGroovyCallInterceptorSetDecorator(GroovyCallSiteInterceptorSet original) {
            this.original = original
        }

        void substituteForCurrentThread(GroovyCallSiteInterceptorSet decorator) {
            if (threadLocalDecorators.get() != original) {
                throw new IllegalStateException("already substituted for the current thread; proper cleanup might have been missed")
            }
            substitutions.incrementAndGet()
            threadLocalDecorators.set(decorator)
        }

        void cancelSubstitutionForCurrentThread() {
            if (threadLocalDecorators.get() == original) {
                throw new IllegalStateException("there was no substitution for the current thread")
            }
            substitutions.decrementAndGet()
            threadLocalDecorators.remove()
        }

        @Override
        List<CallInterceptor> getCallInterceptors(InterceptorsRequest request) {
            return threadLocalDecorators.get().getCallInterceptors(request)
        }
    }
}
