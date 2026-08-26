/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.internal.resources.ResourceLock;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Encapsulates some mutable model, and provides synchronized access to the model.
 */
public interface ModelContainer<T> {

    /**
     * A model container guarding no mutable state.
     */
    ModelContainer<Object> EMPTY = new ModelContainer<Object>() {

        private final Object model = new Object();

        @Override
        public boolean hasMutableState() {
            return true;
        }

        @Override
        public <S extends @Nullable Object> S runWithModelLock(Supplier<S> action) {
            return action.get();
        }

        @Override
        public <S extends @Nullable Object> S fromMutableState(Function<? super Object, ? extends S> factory) {
            return factory.apply(model);
        }

        @Override
        public <S> S forceAccessToMutableState(Function<? super Object, ? extends S> factory) {
            return factory.apply(model);
        }

        @Override
        public void applyToMutableState(Consumer<? super Object> action) {
            action.accept(model);
        }

        @Override
        public @Nullable ResourceLock getAccessLock() {
            return null;
        }

    };

    /**
     * Runs the given function to calculate a value from the mutable model this container guards.
     * <p>
     * Acquires the {@link #getAccessLock() access lock} if present and not already held
     * by the current thread, executes the given action, then releases the lock if acquired.
     */
    <S extends @Nullable Object> S fromMutableState(Function<? super T, ? extends S> factory);

    /**
     * Runs the given supplier, while synchronizing on the model.
     * The mutable state of the model can be used by the calculation, if a reference to it has been retrieved earlier.
     * <p>
     * Acquires the {@link #getAccessLock() access lock} if present and not already held
     * by the current thread, executes the given action, then releases the lock if acquired.
     */
    <S extends @Nullable Object> S runWithModelLock(Supplier<S> action);

    /**
     * DO NOT USE THIS METHOD. It is here to provide some specific backwards compatibility.
     */
    <S> S forceAccessToMutableState(Function<? super T, ? extends S> factory);

    /**
     * Runs the given action on the mutable model this container guards.
     * <p>
     * Acquires the {@link #getAccessLock() access lock} if present and not already held
     * by the current thread, executes the given action, then releases the lock if acquired.
     */
    void applyToMutableState(Consumer<? super T> action);

    /**
     * Returns whether the current thread has access to the mutable model.
     */
    boolean hasMutableState();

    /**
     * Get the resource lock, which when acquired, will permit methods on this
     * container to access the model without blocking. When acquired,
     * {@link #hasMutableState()} will return {@code true}.
     *
     * @return null if this container does not guard its model from concurrent access.
     */
    @Nullable ResourceLock getAccessLock();

}
