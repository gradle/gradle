/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.Action;

/**
 * Tracks whether the current thread is executing a lazy operation.
 * <p>
 * This class is poorly named, and should be renamed to something like "LazyGuard",
 * as the intention of this class is to track whether the current thread is executing
 * a lazy action, so that we can fail other operations in those cases.
 */
public interface MutationGuard {
    /**
     * Wraps the specified action that is executed lazily. When this action
     * is executed, the executing thread is marked as executing a lazy operation.
     *
     * @param action the action to wrap.
     *
     * @return an action
     */
    <T> Action<? super T> wrapLazyAction(Action<? super T> action);

    /**
     * Wraps the specified action that is executed eagerly. When this action
     * is executed, the executing thread is marked as executing an eager operation.
     *
     * @param action the action to wrap.
     *
     * @return an action
     */
    <T> Action<? super T> wrapEagerAction(Action<? super T> action);

    /**
     * Returns {@code true} iff the current thread is executing a lazy operation.
     */
    boolean isLazyContext();

    /**
     * Throws exception if the current thread is executing a lazy action.
     *
     * @param methodName the method name the assertion is testing
     * @param target the target object been asserted on
     */
    void assertEagerContext(String methodName, Object target);

    /**
     * Same as {@link #assertEagerContext(String, Object)}, but the public type
     * of the target may be specified for improved error messages.
     */
    <T> void assertEagerContext(String methodName, T target, Class<T> targetType);
}
