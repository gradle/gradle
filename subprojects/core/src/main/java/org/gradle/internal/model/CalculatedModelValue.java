/*
 * Copyright 2020 the original author or authors.
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
import javax.annotation.concurrent.ThreadSafe;
import java.util.function.Function;

/**
 * Represents a value that is calculated from some mutable state managed by a {@link ModelContainer}, where the calculated value may
 * be used by multiple threads.
 */
@ThreadSafe
public interface CalculatedModelValue<T> {
    /**
     * Returns the current value, failing if not present.
     *
     * <p>May be called by any thread. This method returns immediately and does not block to wait for any currently running or pending calls to {@link #update(Function)} to complete.
     */
    T get() throws IllegalStateException;

    /**
     * Returns the current value, returning {@code null} if not present.
     *
     * <p>May be called by any thread. This method returns immediately and does not block to wait for any currently running or pending calls to {@link #update(Function)} to complete.
     */
    @Nullable
    T getOrNull();

    /**
     * Updates the current value. The function is passed the current value or {@code null} if there is no value, and the function's return value is
     * used as the new value.
     *
     * <p>The calling thread must own the mutable state from which the value is calculated (via {@link ModelContainer#withMutableState(Function)}). At most a single thread
     * will run the update function at a given time. This additional guarantee is because the mutable state lock may be released
     * while the function is running or while waiting to access the value.
     */
    T update(Function<T, T> updateFunction);

    /**
     * Sets the current value.
     *
     * <p>The calling thread< must own the mutable state from which the value is calculated.</p>
     */
    void set(T newValue);
}
