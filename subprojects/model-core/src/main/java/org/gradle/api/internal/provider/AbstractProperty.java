/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nullable;

public abstract class AbstractProperty<T, S extends ValueSupplier> extends AbstractMinimalProvider<T> implements PropertyInternal<T> {
    private static final FinalizedValue<Object> FINALIZED_VALUE = new FinalizedValue<>();
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this property");
    private static final DisplayName DEFAULT_VALIDATION_DISPLAY_NAME = Describables.of("a property");

    private ModelObject producer;
    private DisplayName displayName;
    private FinalizationState<S> state;
    private S value;

    public AbstractProperty(PropertyHost host) {
        state = new NonFinalizedValue<>(host);
    }

    protected void init(S initialValue, S convention) {
        this.value = initialValue;
        this.state.setConvention(convention);
    }

    protected void init(S initialValue) {
        init(initialValue, initialValue);
    }

    @Override
    public boolean isPresent() {
        beforeRead();
        return getSupplier().isPresent();
    }

    @Override
    public void attachOwner(ModelObject owner, DisplayName displayName) {
        this.displayName = displayName;
    }

    @Nullable
    @Override
    protected DisplayName getDeclaredDisplayName() {
        return displayName;
    }

    @Override
    protected DisplayName getTypedDisplayName() {
        return DEFAULT_DISPLAY_NAME;
    }

    @Override
    protected DisplayName getDisplayName() {
        if (displayName == null) {
            return DEFAULT_DISPLAY_NAME;
        }
        return displayName;
    }

    protected DisplayName getValidationDisplayName() {
        if (displayName == null) {
            return DEFAULT_VALIDATION_DISPLAY_NAME;
        }
        return displayName;
    }

    @Override
    public void attachProducer(ModelObject owner) {
        if (this.producer == null) {
            this.producer = owner;
        } else if (this.producer != owner) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(getDisplayName().getCapitalizedDisplayName());
            formatter.append(" is already declared as an output property of ");
            format(this.producer, formatter);
            formatter.append(". Cannot also declare it as an output property of ");
            format(owner, formatter);
            formatter.append(".");
            throw new IllegalStateException(formatter.toString());
        }
    }

    protected S getSupplier() {
        return value;
    }

    @Override
    protected Value<? extends T> calculateOwnValue() {
        beforeRead();
        return calculateOwnValue(value);
    }

    protected abstract Value<? extends T> calculateOwnValue(S value);

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends T> value = calculateOwnExecutionTimeValue();
        if (getProducerTasks().isEmpty()) {
            return value;
        } else {
            return value.withChangingContent();
        }
    }

    protected ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue() {
        return super.calculateExecutionTimeValue();
    }

    /**
     * Returns a diagnostic string describing the current source of value of this property. Should not realize the value.
     */
    protected abstract String describeContents();

    // This method is final - implement describeContents() instead
    @Override
    public final String toString() {
        if (displayName != null) {
            return displayName.toString();
        } else {
            return describeContents();
        }
    }

    @Override
    public void visitProducerTasks(Action<? super Task> visitor) {
        Task task = getProducerTask();
        if (task != null) {
            visitor.execute(task);
        } else {
            getSupplier().visitProducerTasks(visitor);
        }
    }

    @Override
    public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
        Task task = getProducerTask();
        if (task != null) {
            context.add(task);
            return true;
        } else {
            return getSupplier().maybeVisitBuildDependencies(context);
        }
    }

    @Override
    public void finalizeValue() {
        if (state.isNotFinal()) {
            value = finalValue(value);
            state = state.finalState();
        }
    }

    @Override
    public void disallowChanges() {
        state.disallowChanges();
    }

    @Override
    public void finalizeValueOnRead() {
        state.finalizeOnNextGet();
    }

    @Override
    public void implicitFinalizeValue() {
        state.disallowChanges();
        state.finalizeOnNextGet();
    }

    // Should be on the public API. Was not made public for the 6.3 release
    public void disallowUnsafeRead() {
        state.disallowUnsafeRead();
    }

    protected abstract S finalValue(S value);

    protected void setSupplier(S supplier) {
        assertCanMutate();
        this.value = state.explicitValue(supplier);
    }

    protected void setConvention(S convention) {
        assertCanMutate();
        this.value = state.applyConvention(value, convention);
    }

    /**
     * Call prior to reading the value of this property.
     */
    protected void beforeRead() {
        state.beforeRead(getDisplayName());
        if (state.isFinalizeOnRead()) {
            value = finalValue(value);
            state = state.finalState();
        }
    }

    /**
     * Returns the current value of this property, if explicitly defined, otherwise the given default. Does not apply the convention.
     */
    protected S getExplicitValue(S defaultValue) {
        return state.explicitValue(value, defaultValue);
    }

    /**
     * Discards the value of this property, and uses its convention.
     */
    protected void discardValue() {
        assertCanMutate();
        value = state.implicitValue();
    }

    protected void assertCanMutate() {
        state.beforeMutate(getDisplayName());
    }

    @Nullable
    private Task getProducerTask() {
        if (producer == null) {
            return null;
        }
        Task task = producer.getTaskThatOwnsThisObject();
        if (task == null) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node(getDisplayName().getCapitalizedDisplayName());
            formatter.append(" is declared as an output property of ");
            format(producer, formatter);
            formatter.append(" but does not have a task associated with it.");
            throw new IllegalStateException(formatter.toString());
        }
        return task;
    }

    private void format(ModelObject modelObject, TreeFormatter formatter) {
        if (modelObject.getModelIdentityDisplayName() != null) {
            formatter.append(modelObject.getModelIdentityDisplayName().getDisplayName());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else if (modelObject.hasUsefulDisplayName()) {
            formatter.append(modelObject.toString());
            formatter.append(" (type ");
            formatter.appendType(modelObject.getClass());
            formatter.append(")");
        } else {
            formatter.append("an object with type ");
            formatter.appendType(modelObject.getClass());
        }
    }

    private static abstract class FinalizationState<S> {
        public abstract boolean isNotFinal();

        public abstract FinalizationState<S> finalState();

        abstract void setConvention(S convention);

        public abstract void disallowChanges();

        public abstract void finalizeOnNextGet();

        public abstract void disallowUnsafeRead();

        public abstract S explicitValue(S value);

        public abstract S explicitValue(S value, S defaultValue);

        public abstract S applyConvention(S value, S convention);

        public abstract S implicitValue();

        public abstract void beforeRead(DisplayName displayName);

        public abstract boolean isFinalizeOnRead();

        public abstract void beforeMutate(DisplayName displayName);
    }

    private static class NonFinalizedValue<S> extends FinalizationState<S> {

        private final PropertyHost host;
        private boolean explicitValue;
        private boolean finalizeOnNextGet;
        private boolean disallowChanges;
        private boolean disallowUnsafeRead;
        private S convention;

        public NonFinalizedValue(PropertyHost host) {
            this.host = host;
        }

        @Override
        public boolean isNotFinal() {
            return true;
        }

        @Override
        public FinalizationState<S> finalState() {
            return Cast.uncheckedCast(FINALIZED_VALUE);
        }

        @Override
        public boolean isFinalizeOnRead() {
            return finalizeOnNextGet;
        }

        @Override
        public void beforeRead(DisplayName displayName) {
            if (disallowUnsafeRead) {
                String reason = host.beforeRead();
                if (reason != null) {
                    TreeFormatter formatter = new TreeFormatter();
                    formatter.node("Cannot query the value of ");
                    formatter.append(displayName.getDisplayName());
                    formatter.append(" because ");
                    formatter.append(reason);
                    formatter.append(".");
                    throw new IllegalStateException(formatter.toString());
                }
            }
        }

        @Override
        public void beforeMutate(DisplayName displayName) {
            if (disallowChanges) {
                throw new IllegalStateException(String.format("The value for %s cannot be changed any further.", displayName.getDisplayName()));
            }
        }

        @Override
        public void disallowChanges() {
            disallowChanges = true;
        }

        @Override
        public void finalizeOnNextGet() {
            finalizeOnNextGet = true;
        }

        @Override
        public void disallowUnsafeRead() {
            disallowUnsafeRead = true;
            finalizeOnNextGet = true;
        }

        @Override
        public S explicitValue(S value) {
            explicitValue = true;
            return value;
        }

        @Override
        public S explicitValue(S value, S defaultValue) {
            if (!explicitValue) {
                return defaultValue;
            }
            return value;
        }

        @Override
        public S implicitValue() {
            explicitValue = false;
            return convention;
        }

        @Override
        public S applyConvention(S value, S convention) {
            this.convention = convention;
            if (!explicitValue) {
                return convention;
            } else {
                return value;
            }
        }

        @Override
        void setConvention(S convention) {
            this.convention = convention;
        }
    }

    private static class FinalizedValue<S> extends FinalizationState<S> {
        @Override
        public boolean isNotFinal() {
            return false;
        }

        @Override
        public void disallowChanges() {
            // Finalized, so already cannot change
        }

        @Override
        public void finalizeOnNextGet() {
            // Finalized already
        }

        @Override
        public void disallowUnsafeRead() {
            // Finalized so read is safe
        }

        @Override
        public void beforeRead(DisplayName displayName) {
            // Value is available
        }

        @Override
        public boolean isFinalizeOnRead() {
            return false;
        }

        @Override
        public void beforeMutate(DisplayName displayName) {
            throw new IllegalStateException(String.format("The value for %s is final and cannot be changed any further.", displayName.getDisplayName()));
        }

        @Override
        public S explicitValue(S value) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public S explicitValue(S value, S defaultValue) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public S applyConvention(S value, S convention) {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public S implicitValue() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        public FinalizationState<S> finalState() {
            throw new UnsupportedOperationException("Should not be called");
        }

        @Override
        void setConvention(S convention) {
            throw new UnsupportedOperationException("Should not be called");
        }
    }
}
