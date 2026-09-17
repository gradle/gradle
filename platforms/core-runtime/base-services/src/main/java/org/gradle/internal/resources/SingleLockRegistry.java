/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.resources;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A registry where a thread may only hold one of its locks simultaneously.
 * <p>
 * Acquiring more than one lock of this registry on one thread is not permitted.
 */
@NullMarked
public abstract class SingleLockRegistry<K, T extends ResourceLock> extends AbstractResourceLockRegistry<K, T> {

    private final ConcurrentMap<Long, ThreadState<T>> threadStates = new ConcurrentHashMap<>();

    public SingleLockRegistry(ResourceLockCoordinationService coordinationService) {
        super(coordinationService);
    }

    /**
     * Return the lock of this registry held by the current thread, or null when it holds none.
     */
    public final @Nullable T getLockForCurrentThread() {
        return stateForCurrentThread().lock;
    }

    @Override
    public Collection<T> getResourceLocksByCurrentThread() {
        T lock = stateForCurrentThread().lock;
        return lock == null ? ImmutableList.of() : ImmutableList.of(lock);
    }

    @Override
    public boolean holdsLock() {
        return stateForCurrentThread().lock != null;
    }

    @Override
    public boolean holdsLock(ResourceLock lock) {
        return stateForCurrentThread().lock == lock;
    }

    @Override
    public void lockAcquired(ResourceLock resourceLock) {
        ThreadState<T> state = stateForCurrentThread();
        if (!state.mayChange) {
            throw new IllegalStateException("This thread may not acquire more locks.");
        }
        if (state.lock != null) {
            throw new IllegalStateException(String.format(
                "Cannot acquire %s. The current thread already holds %s and may hold only one lock of this kind at a time.",
                resourceLock.getDisplayName(), state.lock.getDisplayName()));
        }
        state.lock = Cast.uncheckedCast(resourceLock);
    }

    @Override
    public void lockReleased(ResourceLock resourceLock) {
        ThreadState<T> state = threadStates.get(currentThreadId());
        if (state == null || !state.mayChange) {
            throw new IllegalStateException("This thread may not release any locks.");
        }
        if (state.lock == resourceLock) {
            state.lock = null;
        }
    }

    @Override
    public <S> S whileDisallowingLockChanges(Factory<S> action) {
        ThreadState<T> state = stateForCurrentThread();
        boolean previous = state.mayChange;
        state.mayChange = false;
        try {
            return action.create();
        } finally {
            state.mayChange = previous;
        }
    }

    @Override
    public boolean mayAttemptToChangeLocks() {
        return stateForCurrentThread().mayChange;
    }

    private ThreadState<T> stateForCurrentThread() {
        long id = currentThreadId();
        ThreadState<T> state = threadStates.get(id);
        if (state == null) {
            state = new ThreadState<>();
            threadStates.put(id, state);
        }
        return state;
    }

    private static class ThreadState<T extends ResourceLock> {
        // Only accessed by the thread itself, so does not require synchronization
        private boolean mayChange = true;
        private @Nullable T lock;
    }

}
