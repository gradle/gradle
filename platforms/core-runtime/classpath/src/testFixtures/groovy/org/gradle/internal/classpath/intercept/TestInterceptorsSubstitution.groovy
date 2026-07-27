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

import javax.annotation.Nullable

/**
 * Provides support for replacing the interceptors for the current thread.
 * Make sure that the call sites that should be affected by the change are only reached after the interceptors have been substituted.
 *
 * The interceptors are substituted per-thread because other threads may be executing unrelated tests.
 */
abstract class TestInterceptorsSubstitution<T> {
    private final T substitution

    TestInterceptorsSubstitution(T substitution) {
        this.substitution = substitution
    }

    abstract T getCurrentInterceptorSet();
    abstract ThreadLocalInterceptorSet<T> decorateWithThreadLocalImpl(T original);
    abstract void setCurrentInterceptorSet(T newInterceptorSet);

    /**
     * Ensures that the global call interceptors are replaced with an implementation that maintains call interceptors per thread.
     * Then sets up the call interceptors for the current thread only.
     */
    void setupForCurrentThread() {
        synchronized (CallInterceptorRegistry.class) {
            maybeSetGlobalCallSiteDecorator().substituteForCurrentThread(substitution)
        }
    }

    private ThreadLocalInterceptorSet<T> maybeSetGlobalCallSiteDecorator() {
        def interceptorFactorySet = getCurrentInterceptorSet()
        if (interceptorFactorySet !instanceof ThreadLocalInterceptorSet) {
            interceptorFactorySet = decorateWithThreadLocalImpl(interceptorFactorySet)
            setCurrentInterceptorSet(interceptorFactorySet as T)
        }
        return interceptorFactorySet as ThreadLocalInterceptorSet<T>
    }

    /**
     * Cancels the call interceptors substitution for the current thread.
     * If the global implementation does not have any other active call interceptors in the other threads, reverts the
     * global call interceptors implementation.
     */
    void cleanupForCurrentThread() {
        synchronized (CallInterceptorRegistry.class) {
            substitutionIfPresent()?.cancelSubstitutionForCurrentThread()
            maybeRevertGlobalJvmBytecodeInterceptorFactorySet()
        }
    }

    @Nullable
    private ThreadLocalInterceptorSet<T> substitutionIfPresent() {
        def decorator = getCurrentInterceptorSet()
        if (decorator instanceof ThreadLocalInterceptorSet) {
            return decorator as ThreadLocalInterceptorSet<T>
        }
        return null
    }

    private void maybeRevertGlobalJvmBytecodeInterceptorFactorySet() {
        def interceptorFactorySet = getCurrentInterceptorSet()
        if (interceptorFactorySet instanceof ThreadLocalInterceptorSet) {
            if (interceptorFactorySet.isEmpty()) {
                setCurrentInterceptorSet(interceptorFactorySet.original as T)
            }
        }
    }
}
