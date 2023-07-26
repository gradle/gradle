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

package org.gradle.internal.classpath

import org.codehaus.groovy.runtime.callsite.CallSite
import org.gradle.internal.classpath.intercept.CallInterceptor
import org.gradle.internal.classpath.intercept.CallInterceptorResolver
import org.gradle.internal.classpath.intercept.CallInterceptorsSet
import org.gradle.internal.classpath.intercept.CallSiteDecorator
import org.gradle.internal.classpath.intercept.InterceptScope

import javax.annotation.Nullable
import java.lang.invoke.MethodHandles
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
                    new CallInterceptorsSet(GroovyCallInterceptorsProvisionTools.getInterceptorsFromProvider(substitutionProvider).stream())
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
    private static ThreadLocalCallSiteDecoratorSubstitution substitutionIfPresent() {
        def decorator = Instrumented.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator instanceof ThreadLocalCallSiteDecoratorSubstitution) {
            return decorator as ThreadLocalCallSiteDecoratorSubstitution
        }
        return null
    }

    private static ThreadLocalCallSiteDecoratorSubstitution maybeSetGlobalCallSiteDecorator() {
        def decorator = Instrumented.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator !instanceof ThreadLocalCallSiteDecoratorSubstitution) {
            decorator = new ThreadLocalCallSiteDecoratorSubstitution(decorator)
            Instrumented.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator = decorator
        }
        return decorator as ThreadLocalCallSiteDecoratorSubstitution
    }

    private static void maybeRevertGlobalCallSiteDecorator() {
        def decorator = Instrumented.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator
        if (decorator instanceof ThreadLocalCallSiteDecoratorSubstitution) {
            if (decorator.isEmpty()) {
                Instrumented.GroovyCallInterceptorInternalTesting.currentGroovyCallSiteDecorator = decorator.original
            }
        }
    }

    private static class ThreadLocalCallSiteDecoratorSubstitution implements CallSiteDecorator, CallInterceptorResolver {

        public final CallSiteDecorator original

        private final AtomicInteger substitutions = new AtomicInteger(0)
        private final ThreadLocal<CallSiteDecorator> threadLocalDecorators = ThreadLocal.withInitial { original }

        boolean isEmpty() {
            substitutions.get() == 0
        }

        ThreadLocalCallSiteDecoratorSubstitution(CallSiteDecorator original) {
            this.original = original
        }

        void substituteForCurrentThread(CallSiteDecorator decorator) {
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
        CallSite maybeDecorateGroovyCallSite(CallSite originalCallSite) {
            threadLocalDecorators.get().maybeDecorateGroovyCallSite(originalCallSite)
        }

        @Override
        java.lang.invoke.CallSite maybeDecorateIndyCallSite(java.lang.invoke.CallSite originalCallSite, MethodHandles.Lookup caller, String callType, String name, int flags) {
            threadLocalDecorators.get().maybeDecorateIndyCallSite(originalCallSite, caller, callType, name, flags)
        }

        @Override
        CallInterceptor resolveCallInterceptor(InterceptScope scope) {
            def decorator = threadLocalDecorators.get()
            if (decorator instanceof CallInterceptorResolver) {
                return decorator.resolveCallInterceptor(scope)
            }
            return null
        }

        @Override
        boolean isAwareOfCallSiteName(String name) {
            def decorator = threadLocalDecorators.get()
            if (decorator instanceof CallInterceptorResolver) {
                return decorator.isAwareOfCallSiteName(name);
            }
            return false;
        }
    }
}
