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

import org.gradle.internal.Try;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents a calculated immutable value that is calculated once and then consumed by multiple threads.
 */
@ThreadSafe
public interface CalculatedValue<T> {
    /**
     * Returns the value, failing if it has not been calculated.
     * Does not calculate the value on demand and does not block if the value is currently being calculated.
     *
     * <p>Rethrows any exception that happened while calculating the value</p>
     */
    T get() throws IllegalStateException;

    /**
     * Returns the value, or null if it has not been calculated.
     * Does not calculate the value on demand and does not block if the value is currently being calculated.
     *
     * <p>Rethrows any exception that happened while calculating the value</p>
     */
    @Nullable
    T getOrNull();

    /**
     * Returns the result of calculating the value, failing if it has not been calculated.
     * Does not calculate the value on demand and does not block if the value is currently being calculated.
     */
    Try<T> getValue() throws IllegalStateException;

    /**
     * Returns true if this value is already calculated. Note that some other thread may currently be calculating the value.
     */
    boolean isFinalized();

    /**
     * Calculates the value, if not already calculated. Collects any exception and does not rethrow them.
     * Blocks until the value is finalized, either by this thread or some other thread.
     */
    void finalizeIfNotAlready();

    /**
     * Returns the resource that will be required to calculate this value.
     */
    ModelContainer<?> getResourceToLock();
}
