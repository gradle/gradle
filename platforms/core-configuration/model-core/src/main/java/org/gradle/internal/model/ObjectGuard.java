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

import org.gradle.internal.work.Synchronizer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides a thread-safe way to initialize and access a value.
 */
@NullMarked
public class ObjectGuard<T> {

    private volatile @Nullable T value;
    private final Synchronizer synchronizer;

    public ObjectGuard(Synchronizer synchronizer) {
        this.synchronizer = synchronizer;
    }

    /**
     * Set the value of the object.
     *
     * @throws IllegalStateException if the object has already been initialized.
     */
    public void initialize(T value) {
        synchronizer.withLock(() -> {
            if (this.value != null) {
                throw new IllegalStateException("Object has already been initialized.");
            }
            this.value = value;
        });
    }

    /**
     * Returns true if the object has been initialized and not yet destroyed.
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Returns the value of the object without acquiring a lock.
     * <p>
     * This method should be avoided.
     */
    public T unsafeGet() {
        T local = value;
        if (local == null) {
            throw new IllegalStateException("Object has not been initialized.");
        }
        return local;
    }

    /**
     * Executes the given function with the value of the object while holding
     * the lock and returns the result.
     */
    public <V extends @Nullable Object> V fromValue(Function<? super T, V> function) {
        return synchronizer.withLock(() -> function.apply(unsafeGet()));
    }

    /**
     * Executes the given action with the value of the object while holding
     * the lock.
     */
    public void runWithValue(Consumer<? super T> consumer) {
        synchronizer.withLock(() -> consumer.accept(unsafeGet()));
    }

    /**
     * If initialized, executes the given action with the value of the object
     * while holding the lock and then uninitializes the object.
     */
    public void destroy(Consumer<? super T> consumer) {
        synchronizer.withLock(() -> {
            T local = value;
            if (local != null) {
                try {
                    consumer.accept(local);
                } finally {
                    this.value = null;
                }
            }
        });
    }

}
