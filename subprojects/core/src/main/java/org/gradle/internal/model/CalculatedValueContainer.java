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

import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents a calculated immutable value that should be calculated once and then consumed by multiple threads.
 *
 * <p>This type is intended to represent values that are calculated as nodes in the work graph, but which may also be calculated
 * on demand.
 * </p>
 */
@ThreadSafe
public class CalculatedValueContainer<T> {
    private final DisplayName displayName;
    private CalculatedValue<? extends T> value;
    private volatile Try<T> result;

    public CalculatedValueContainer(CalculatedValue<? extends T> value) {
        this.value = value;
        this.displayName = value.getDisplayName();
    }

    /**
     * Returns the value, failing if it has not been calculated. Does not calculate the value on demand.
     */
    public T get() throws IllegalStateException {
        return getValue().get();
    }

    /**
     * Returns the value, failing if it has not been calculated. Does not calculate the value on demand.
     */
    public Try<T> getValue() throws IllegalStateException {
        Try<T> result = this.result;
        if (result == null) {
            throw new IllegalStateException(String.format("%s has not been calculated yet.", displayName.getCapitalizedDisplayName()));
        }
        return result;
    }

    /**
     * Calculates the value, if not already calculated.
     */
    public void calculateNow(@Nullable NodeExecutionContext context) {
        synchronized (this) {
            if (result == null) {
                result = Try.ofFailable(() -> value.calculateValue(context));
                value = null;
            }
        }
    }
}
