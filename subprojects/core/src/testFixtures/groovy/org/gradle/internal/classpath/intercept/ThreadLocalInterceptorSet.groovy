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

import java.util.concurrent.atomic.AtomicInteger

abstract class ThreadLocalInterceptorSet<T> {

    private final AtomicInteger substitutions = new AtomicInteger(0)
    protected final ThreadLocal<T> threadLocalDecorators = ThreadLocal.withInitial { original }

    final T original

    ThreadLocalInterceptorSet(T original) {
        this.original = original
    }

    boolean isEmpty() {
        substitutions.get() == 0
    }

    void substituteForCurrentThread(T substitution) {
        if (threadLocalDecorators.get() != original) {
            throw new IllegalStateException("already substituted for the current thread; proper cleanup might have been missed")
        }
        substitutions.incrementAndGet()
        threadLocalDecorators.set(substitution)
    }

    void cancelSubstitutionForCurrentThread() {
        if (threadLocalDecorators.get() == original) {
            throw new IllegalStateException("there was no substitution for the current thread")
        }
        substitutions.decrementAndGet()
        threadLocalDecorators.remove()
    }
}
