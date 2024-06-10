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
import org.gradle.api.internal.initialization.RootScriptDomainObjectContext;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.DisplayName;
import org.gradle.internal.Try;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.service.ServiceLookupException;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents a calculated immutable value that is calculated once and then consumed by multiple threads.
 *
 * <p>This type is intended to contain values that are calculated as nodes in the work graph, but which may also be calculated
 * on demand. An instance of this type can be used as a node in the work graph.
 * </p>
 *
 * <p>Note that when used as a work node, any failure to calculate the value is collected and not rethrown. This means that the node is considered to have succeeded and any dependent
 * nodes will execute, and the exception will be rethrown when the value is queried.
 * </p>
 *
 * <p>You should use {@link CalculatedValueContainerFactory} to create instances of this type.</p>
 *
 * <p>This type can hold null as the computed value.</p>
 */
@ThreadSafe
public class CalculatedValueContainer<T, S extends ValueCalculator<? extends T>> implements CalculatedValue<T>, WorkNodeAction {
    // TODO(https://github.com/gradle/gradle/issues/24767): with JSpecify, the nullable nature of the type argument <T> should be expressed as <T extends @Nullable Object>.
    //  We cannot use this syntax until adopting JSpecify with e.g. Jetbrains Annotations, because IDEA wrongly treats all usages as having a nullable type, even when
    //  it is explicitly spelled.

    private final DisplayName displayName;
    // Null when the value has been calculated and assigned to the result field. When not null the result has not been calculated
    // or is currently being calculated or has just been calculated. It is possible for both this field and the result field to be
    // non-null at the same time for a brief period just after the result has been calculated
    @Nullable
    private volatile CalculationState<T, S> calculationState;
    // Not null when the value has been calculated. When not null the result has not been calculated or is currently being calculated
    @Nullable
    private volatile Try<T> result;

    /**
     * Creates a container for a value that is yet to be produced.
     *
     * Note: this is package protected. Use {@link CalculatedValueContainerFactory} instead.
     */
    CalculatedValueContainer(DisplayName displayName, S supplier, ProjectLeaseRegistry projectLeaseRegistry, NodeExecutionContext defaultContext) {
        this.displayName = displayName;
        this.calculationState = new CalculationState<>(displayName, supplier, projectLeaseRegistry, defaultContext);
    }

    /**
     * Creates a container for a value that has already been produced. For example, the value might have been restored from the configuration cache.
     *
     * Note: this is package protected. Use {@link CalculatedValueContainerFactory} instead.
     */
    CalculatedValueContainer(DisplayName displayName, T value) {
        this.displayName = displayName;
        this.result = Try.successful(value);
    }

    @Override
    public String toString() {
        return displayName.getCapitalizedDisplayName();
    }

    @Override
    public T get() throws IllegalStateException {
        return getValue().get();
    }

    @Override
    public Try<T> getValue() throws IllegalStateException {
        Try<T> result = this.result;
        if (result == null) {
            throw new IllegalStateException(String.format("Value for %s has not been calculated yet.", displayName));
        }
        return result;
    }

    @Override
    public boolean isFinalized() {
        return result != null;
    }

    /**
     * Returns the supplier of the value, failing if the value has already been calculated and the supplier no longer available.
     *
     * Note: some other thread may currently be calculating the value
     */
    public S getSupplier() throws IllegalStateException {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState == null) {
            throw new IllegalStateException(String.format("Value for %s has already been calculated.", displayName));
        }
        return calculationState.supplier;
    }

    @Override
    public boolean usesMutableProjectState() {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState != null) {
            return calculationState.supplier.usesMutableProjectState();
        } else {
            // Value has already been calculated, so no longer needs project state
            return false;
        }
    }

    @Override
    public Project getOwningProject() {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState != null) {
            return calculationState.supplier.getOwningProject();
        } else {
            // Value has already been calculated, so no longer needs project state
            return null;
        }
    }

    @Override
    public ModelContainer<?> getResourceToLock() {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState != null && calculationState.supplier.usesMutableProjectState()) {
            return calculationState.supplier.getOwningProject().getOwner();
        } else {
            return RootScriptDomainObjectContext.INSTANCE.getModel();
        }
    }

    /**
     * Visits the dependencies required to calculate the value.
     */
    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState != null) {
            calculationState.supplier.visitDependencies(context);
        } // else, already calculated so has no dependencies
    }

    @Nullable
    @Override
    public WorkNodeAction getPreExecutionNode() {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState != null) {
            return calculationState.supplier.getPreExecutionAction();
        } else {
            return null;
        }
    }

    /**
     * Calculates the value, if not already calculated. Collects and does not rethrow failures.
     */
    @Override
    public void run(NodeExecutionContext context) {
        finalizeIfNotAlready(context);
    }

    @Override
    public void finalizeIfNotAlready() {
        finalizeIfNotAlready(null);
    }

    private void finalizeIfNotAlready(@Nullable NodeExecutionContext context) {
        CalculationState<T, S> calculationState = this.calculationState;
        if (calculationState == null) {
            // Already calculated
            return;
        }
        calculationState.attachValue(this, context);
    }

    private static class CalculationState<T, S extends ValueCalculator<? extends T>> {
        final ReentrantLock lock = new ReentrantLock();
        final DisplayName displayName;
        final S supplier;
        final ProjectLeaseRegistry projectLeaseRegistry;
        final NodeExecutionContext defaultContext;
        boolean done;

        public CalculationState(DisplayName displayName, S supplier, ProjectLeaseRegistry projectLeaseRegistry, NodeExecutionContext defaultContext) {
            this.displayName = displayName;
            this.supplier = supplier;
            this.projectLeaseRegistry = projectLeaseRegistry;
            this.defaultContext = defaultContext;
        }

        // Can be called multiple times
        void attachValue(CalculatedValueContainer<T, ?> owner, @Nullable NodeExecutionContext context) {
            acquireLock();
            try {
                if (done) {
                    // Already calculated
                    return;
                }
                done = true;

                // Attach result and discard calculation state
                owner.result = Try.ofFailable(() -> {
                    NodeExecutionContext effectiveContext = context;
                    if (effectiveContext == null) {
                        effectiveContext = new GlobalContext(defaultContext);
                    }
                    return supplier.calculateValue(effectiveContext);
                });
                owner.calculationState = null;
            } finally {
                releaseLock();
            }
        }

        private void acquireLock() {
            if (lock.tryLock()) {
                // Lock not contended - can proceed
                return;
            }
            // Lock is contended, so release project locks while waiting to acquire the lock
            projectLeaseRegistry.blocking(lock::lock);
        }

        private void releaseLock() {
            lock.unlock();
        }
    }

    /**
     * Used when calculating the value outside of an execution graph.
     * <p>
     * In that case we need to use the global context and not the context that would be created as
     * part of executing the execution graph.
     */
    private static class GlobalContext implements NodeExecutionContext {
        private final NodeExecutionContext delegate;

        public GlobalContext(NodeExecutionContext delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T getService(Class<T> type) throws ServiceLookupException {
            return delegate.getService(type);
        }

        @Override
        public boolean isPartOfExecutionGraph() {
            return false;
        }
    }
}
