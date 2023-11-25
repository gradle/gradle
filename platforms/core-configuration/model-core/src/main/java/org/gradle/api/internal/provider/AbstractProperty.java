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

import org.gradle.api.Task;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public abstract class AbstractProperty<T, S extends ValueSupplier> extends AbstractMinimalProvider<T> implements PropertyInternal<T> {
    private static final DisplayName DEFAULT_DISPLAY_NAME = Describables.of("this property");
    private static final DisplayName DEFAULT_VALIDATION_DISPLAY_NAME = Describables.of("a property");

    private ModelObject producer;
    private DisplayName displayName;
    private ValueState<S> state;
    private S value;

    public AbstractProperty(PropertyHost host) {
        state = ValueState.newState(host);
    }

    protected void init(S initialValue, S convention) {
        this.value = initialValue;
        this.state.setConvention(convention);
    }

    protected void init(S initialValue) {
        init(initialValue, initialValue);
    }

    /**
     * A simple getter that checks if this property has been finalized.
     *
     * @return {@code true} if this property has been finalized, {@code false} otherwise
     */
    public boolean isFinalized() {
        return state.isFinalized();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        beforeRead(consumer);
        try {
            return getSupplier().calculatePresence(consumer);
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    @Override
    public void attachOwner(@Nullable ModelObject owner, DisplayName displayName) {
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

    protected Value<? extends T> calculateOwnValueNoProducer(ValueConsumer consumer) {
        beforeReadNoProducer(consumer);
        return doCalculateValue(consumer);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        beforeRead(consumer);
        return doCalculateValue(consumer);
    }

    @Nonnull
    private Value<? extends T> doCalculateValue(ValueConsumer consumer) {
        try {
            return calculateValueFrom(value, consumer);
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    protected abstract Value<? extends T> calculateValueFrom(S value, ValueConsumer consumer);

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        ExecutionTimeValue<? extends T> value = calculateOwnExecutionTimeValue(this.value);
        if (getProducerTask() == null) {
            return value;
        } else {
            return value.withChangingContent();
        }
    }

    protected abstract ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue(S value);

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
    public ValueProducer getProducer() {
        Task task = getProducerTask();
        if (task != null) {
            return ValueProducer.task(task);
        } else {
            return getSupplier().getProducer();
        }
    }

    @Override
    public void finalizeValue() {
        if (state.shouldFinalize(this.getDisplayName(), producer)) {
            finalizeNow(ValueConsumer.IgnoreUnsafeRead);
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
        state.disallowChangesAndFinalizeOnNextGet();
    }

    public void disallowUnsafeRead() {
        state.disallowUnsafeRead();
    }

    protected abstract S finalValue(S value, ValueConsumer consumer);

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
    protected void beforeRead(ValueConsumer consumer) {
        beforeRead(producer, consumer);
    }

    protected void beforeReadNoProducer(ValueConsumer consumer) {
        beforeRead(null, consumer);
    }

    private void beforeRead(@Nullable ModelObject effectiveProducer, ValueConsumer consumer) {
        state.finalizeOnReadIfNeeded(this.getDisplayName(), effectiveProducer, consumer, this::finalizeNow);
    }

    private void finalizeNow(ValueConsumer consumer) {
        try {
            value = finalValue(value, state.forUpstream(consumer));
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to calculate the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
        state = state.finalState();
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
        state.beforeMutate(this.getDisplayName());
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

    @Contextual
    public static class PropertyQueryException extends RuntimeException {
        public PropertyQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
