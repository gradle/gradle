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

import org.gradle.api.Project;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Represents a calculated immutable value that should be calculated once and then consumed by multiple threads.
 *
 * <p>This type is intended to contain values that are calculated as nodes in the work graph, but which may also be calculated
 * on demand. An instance of this type can be used as a node in the work graph.
 * </p>
 *
 * <p>Note that when used as a work node, any failure to calculate the value is collected and not rethrown. This means that the node is considered to have succeeded and any dependent
 * nodes will execute, and the exception will be rethrown when the value is queried.
 * </p>
 */
@ThreadSafe
public class CalculatedValueContainer<T, S extends ValueCalculator<? extends T>> implements WorkNodeAction {
    private final DisplayName displayName;
    private volatile S supplier;
    private volatile Try<T> result;

    private CalculatedValueContainer(DisplayName displayName, S supplier) {
        this.displayName = displayName;
        this.supplier = supplier;
    }

    private CalculatedValueContainer(DisplayName displayName, T value) {
        this.displayName = displayName;
        this.result = Try.successful(value);
    }

    /**
     * Creates a container for a value that is yet to be produced.
     */
    public static <T, S extends ValueCalculator<? extends T>> CalculatedValueContainer<T, S> of(DisplayName displayName, S supplier) {
        return new CalculatedValueContainer<>(displayName, supplier);
    }

    /**
     * Creates a container for a value that has already been produced. For example, the value might have been restored from the configuration cache.
     */
    public static <T, S extends ValueCalculator<? extends T>> CalculatedValueContainer<T, S> of(DisplayName displayName, T value) {
        return new CalculatedValueContainer<>(displayName, value);
    }

    @Override
    public String toString() {
        return displayName.getCapitalizedDisplayName();
    }

    /**
     * Returns the value, failing if it has not been calculated. Does not calculate the value on demand.
     *
     * <p>Rethrows any failure happened while calculating the value</p>
     */
    public T get() throws IllegalStateException {
        return getValue().get();
    }

    /**
     * Returns the value, or null if it has not been calculated. Does not calculate the value on demand.
     *
     * <p>Rethrows any failure happened while calculating the value</p>
     */
    public T getOrNull() {
        Try<T> result = this.result;
        if (result != null) {
            return result.get();
        } else {
            return null;
        }
    }

    /**
     * Returns the result of calculating the value, failing if it has not been calculated. Does not calculate the value on demand.
     */
    public Try<T> getValue() throws IllegalStateException {
        Try<T> result = this.result;
        if (result == null) {
            throw new IllegalStateException(String.format("Value for %s has not been calculated yet.", displayName));
        }
        return result;
    }

    /**
     * Returns the supplier of the value, failing if the value has already been calculated and the supplier no longer available.
     */
    public S getSupplier() throws IllegalStateException {
        S supplier = this.supplier;
        if (supplier == null) {
            throw new IllegalStateException(String.format("Value for %s has already been calculated.", displayName));
        }
        return supplier;
    }

    @Override
    public boolean usesMutableProjectState() {
        S supplier = this.supplier;
        if (supplier != null) {
            return supplier.usesMutableProjectState();
        } else {
            // Value has already been calculated, so no longer needs project state
            return false;
        }
    }

    @Override
    public Project getOwningProject() {
        S supplier = this.supplier;
        if (supplier != null) {
            return supplier.getOwningProject();
        } else {
            // Value has already been calculated, so no longer needs project state
            return null;
        }
    }

    /**
     * Visits the dependencies required to calculate the value.
     */
    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        S supplier = this.supplier;
        if (supplier != null) {
            supplier.visitDependencies(context);
        } // else, already calculated so has no dependencies
    }

    /**
     * Calculates the value, if not already calculated. Collects and does not rethrow failures.
     */
    @Override
    public void run(NodeExecutionContext context) {
        calculateIfNotAlready(context);
    }

    /**
     * Calculates the value, if not already calculated. Collects and does not rethrow failures.
     */
    public void calculateIfNotAlready(@Nullable NodeExecutionContext context) {
        synchronized (this) {
            if (result == null) {
                result = Try.ofFailable(() -> {
                    T value = supplier.calculateValue(context);
                    if (value == null) {
                        throw new IllegalStateException(String.format("Calculated value for %s cannot be null.", displayName));
                    }
                    return value;
                });
                // Discard the supplier
                supplier = null;
            }
        }
    }
}
