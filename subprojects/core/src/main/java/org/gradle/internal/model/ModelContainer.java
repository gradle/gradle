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

import org.gradle.internal.Factory;

import javax.annotation.Nullable;

/**
 * Encapsulates some mutable model, and provides synchronized access to the model.
 */
public interface ModelContainer {

    /**
     * Runs the given action against the public mutable model. Applies best effort synchronization to prevent concurrent access to a particular project from multiple threads.
     * However, it is currently easy for state to leak from one project to another so this is not a strong guarantee.
     *
     * <p>It is usually a better option to use {@link #newCalculatedValue(Object)} instead of this method.</p>
     *
     * <p>Note also that the mutable state should be passed to the action, but is not yet. This is to help with migration.
     */
    <T> T withMutableState(Factory<? extends T> factory);

    /**
     * Runs the given action against the public mutable model. Applies best effort synchronization to prevent concurrent access to a particular project from multiple threads.
     * However, it is currently easy for state to leak from one project to another so this is not a strong guarantee.
     *
     * <p>Note also that the mutable state should be passed to the action, but is not yet. This is to help with migration.
     */
    void withMutableState(Runnable runnable);

    /**
     * Returns whether or not the current thread has access to the mutable model.
     */
    boolean hasMutableState();

    /**
     * Creates a new container for a value that is calculated from the mutable state of this container, and then reused by multiple threads.
     */
    <T> CalculatedModelValue<T> newCalculatedValue(@Nullable T initialValue);
}
