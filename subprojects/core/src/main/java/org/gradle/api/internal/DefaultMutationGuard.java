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
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.internal.exceptions.Contextual;

public class DefaultMutationGuard implements MutationGuard {

    private final ThreadLocal<Boolean> mutationGuardState = ThreadLocal.withInitial(SerializableLambdas.supplier(() -> true));

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
        boolean mutationAllowed = mutationGuardState.get();
        removeThreadLocalStateIfMutationAllowed(mutationAllowed);
        return !mutationAllowed;
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
                boolean oldIsMutationAllowed = mutationGuardState.get();
                mutationGuardState.set(allowMutationMethods);
                try {
                    action.execute(t);
                } finally {
                    setMutationGuardState(oldIsMutationAllowed);
                }
            }
        };
    }

    private void setMutationGuardState(boolean newState) {
        if (newState) {
            removeThreadLocalStateIfMutationAllowed(true);
        } else {
            mutationGuardState.set(false);
        }
    }

    /**
     * Removes the thread local for `mutationGuardState` if its value is the default value (true).
     *
     * There are many instances of `DefaultMutationGuard` in a Gradle run, e.g. one for each configuration.
     * Each of those instances creates a new thread local for `Daemon worker`.
     * After a build, those thread local instances should be removed from the ThreadLocalMap by garbage collection automatically.
     * It looks like CMS does a good job for removing the unused entries from the ThreadLocalMap, though G1 does not.
     * So if you are running builds in quick succession, e.g. for profiling, there can be a quick slowdown after some time.
     *
     * This methods removes the elements from the ThreadLocalMap when possible, thus avoiding the problem.
     *
     * See https://github.com/gradle/gradle/issues/13835.
     */
    private void removeThreadLocalStateIfMutationAllowed(boolean mutationAllowed) {
        if (mutationAllowed) {
            mutationGuardState.remove();
        }
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
