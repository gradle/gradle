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
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.internal.exceptions.Contextual;
import org.jspecify.annotations.Nullable;

public class DefaultMutationGuard implements MutationGuard {

    /**
     * The mutation state of the current thread. Null means the default state: mutation is allowed.
     * <p>
     * Intentionally not using {@link ThreadLocal#withInitial} with the default value. There are many
     * instances of this class (e.g. one per configuration), each with its own thread local, and reads
     * are far more common than wrapped executions. Representing the default state as an absent entry
     * means reads never write to the thread local map and an entry only exists on a thread while a
     * wrapped action is executing on it. So, unused entries never accumulate in long-lived threads.
     *
     * See <a href="https://github.com/gradle/gradle/issues/13835">gradle/gradle#13835</a>.
     */
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<@Nullable Boolean> mutationGuardState = new ThreadLocal<>();

    @Override
    public <T> Action<? super T> wrapLazyAction(Action<? super T> action) {
        return newActionWithMutation(action, false);
    }

    @Override
    public <T> Action<? super T> wrapEagerAction(Action<? super T> action) {
        return newActionWithMutation(action, true);
    }

    @Override
    public boolean isLazyContext() {
        Boolean mutationAllowed = mutationGuardState.get();
        return mutationAllowed != null && !mutationAllowed;
    }

    @Override
    public void assertEagerContext(String methodName, Object target) {
        if (isLazyContext()) {
            throw createIllegalStateException(new DslObject(target).getPublicType().getConcreteClass(), methodName, target);
        }
    }

    @Override
    public <T> void assertEagerContext(String methodName, T target, Class<T> targetType) {
        if (isLazyContext()) {
            throw createIllegalStateException(targetType, methodName, target);
        }
    }

    private <T> Action<? super T> newActionWithMutation(final Action<? super T> action, final boolean allowMutationMethods) {
        return new Action<T>() {
            @Override
            public void execute(T t) {
                Boolean oldState = mutationGuardState.get();
                mutationGuardState.set(allowMutationMethods);
                try {
                    action.execute(t);
                } finally {
                    if (oldState == null) {
                        // Restore the default state by removing the entry rather than storing the
                        // default value. There are many instances of this class in a Gradle invocation,
                        // e.g., one for each configuration, each with its own thread local. Entries
                        // left behind would accumulate in the thread local maps of long-lived
                        // daemon threads until their guard becomes unreachable, and stale entries
                        // are only cleaned up lazily, degrading all thread local access on those
                        // threads. Removing here guarantees an entry exists only while a wrapped
                        // action is executing.
                        // See https://github.com/gradle/gradle/issues/13835.
                        mutationGuardState.remove();
                    } else {
                        mutationGuardState.set(oldState);
                    }
                }
            }
        };
    }

    private static <T> IllegalStateException createIllegalStateException(Class<T> targetType, String methodName, T target) {
        return new IllegalMutationException(String.format("%s#%s on %s cannot be executed in the current context.", targetType.getSimpleName(), methodName, target));
    }

    @Contextual
    private static class IllegalMutationException extends IllegalStateException {
        public IllegalMutationException(String message) {
            super(message);
        }
    }
}
