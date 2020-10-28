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

import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Encapsulates some mutable model, and provides synchronized access to the model.
 */
public interface ModelContainer<T> {

    /**
     * Runs the given function to calculate a value from the public mutable model. Applies best effort synchronization to prevent concurrent access to a particular project from multiple threads.
     * However, it is currently easy for state to leak from one project to another so this is not a strong guarantee.
     *
     * <p>It is usually a better option to use {@link #newCalculatedValue(Object)} instead of this method.</p>
     */
    <S> S fromMutableState(Function<? super T, ? extends S> factory);

    /**
     * DO NOT USE THIS METHOD. It is here to provide some specific backwards compatibility.
     */
    <S> S forceAccessToMutableState(Function<? super T, ? extends S> factory);

    /**
     * Runs the given action against the public mutable model. Applies best effort synchronization to prevent concurrent access to a particular project from multiple threads.
     * However, it is currently easy for state to leak from one project to another so this is not a strong guarantee.
     */
    void applyToMutableState(Consumer<? super T> action);

    /**
     * Returns whether or not the current thread has access to the mutable model.
     */
    boolean hasMutableState();

    /**
     * Creates a new container for a value that is calculated from the mutable state of this container, and then reused by multiple threads.
     */
    <S> CalculatedModelValue<S> newCalculatedValue(@Nullable S initialValue);
}
