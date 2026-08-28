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

package org.gradle.internal.model;

import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class DefaultCalculatedModelValue<T extends @Nullable Object> implements CalculatedModelValue<T> {

    private final ReentrantLock lock = new ReentrantLock();

    private final ProjectLeaseRegistry projectLeaseRegistry;
    private final ModelContainer<?> owner;

    private volatile T value;

    public DefaultCalculatedModelValue(
        ModelContainer<?> owner,
        ProjectLeaseRegistry projectLeaseRegistry,
        T initialValue
    ) {
        this.projectLeaseRegistry = projectLeaseRegistry;
        this.value = initialValue;
        this.owner = owner;
    }

    @Override
    public T get() throws IllegalStateException {
        T currentValue = getOrNull();
        if (currentValue == null) {
            throw new IllegalStateException("No calculated value is available for " + owner);
        }
        return currentValue;
    }

    @Override
    public T getOrNull() {
        // Grab the current value, ignore updates that may be happening
        return value;
    }

    @Override
    public void set(T newValue) {
        assertCanMutate();
        value = newValue;
    }

    @Override
    public T update(Function<T, T> updateFunction) {
        acquireUpdateLock();
        try {
            T newValue = updateFunction.apply(value);
            value = newValue;
            return newValue;
        } finally {
            releaseUpdateLock();
        }
    }

    private void acquireUpdateLock() {
        // It's important that we do not block waiting for the lock while holding the project mutation lock.
        // Doing so can lead to deadlocks.
        assertCanMutate();

        if (lock.tryLock()) {
            // Update lock was not contended, can keep holding the project locks
            return;
        }

        // Another thread holds the update lock, release the project locks and wait for the other thread to finish the update
        projectLeaseRegistry.blocking(lock::lock);
    }

    private void assertCanMutate() {
        if (!owner.hasMutableState()) {
            throw new IllegalStateException("Current thread does not hold the state lock for " + owner);
        }
    }

    private void releaseUpdateLock() {
        lock.unlock();
    }

}
