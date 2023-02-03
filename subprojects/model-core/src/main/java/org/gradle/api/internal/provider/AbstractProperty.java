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
import org.gradle.internal.Cast;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nonnull;
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

    /**
     * A simple getter that checks if this property has been finalized.
     *
     * @return {@code true} if this property has been finalized, {@code false} otherwise
     */
    public boolean isFinalized() {
        return state instanceof FinalizedValue;
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        beforeRead(producer, consumer);
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
        beforeRead(null, consumer);
        return doCalculateValue(consumer);
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        beforeRead(producer, consumer);
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
        if (state.shouldFinalize(getDisplayName(), producer)) {
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
        state.disallowChanges();
        state.finalizeOnNextGet();
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

    private void beforeRead(@Nullable ModelObject effectiveProducer, ValueConsumer consumer) {
        if (state.maybeFinalizeOnRead(getDisplayName(), effectiveProducer, consumer)) {
            finalizeNow(state.forUpstream(consumer));
        }
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

    @Contextual
    public static class PropertyQueryException extends RuntimeException {
        public PropertyQueryException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static abstract class FinalizationState<S> {
        public abstract boolean shouldFinalize(DisplayName displayName, @Nullable ModelObject producer);

        public abstract FinalizationState<S> finalState();

        abstract void setConvention(S convention);

        public abstract void disallowChanges();

        public abstract void finalizeOnNextGet();

        public abstract void disallowUnsafeRead();

        public abstract S explicitValue(S value);

        public abstract S explicitValue(S value, S defaultValue);

        public abstract S applyConvention(S value, S convention);

        public abstract S implicitValue();

        public abstract boolean maybeFinalizeOnRead(DisplayName displayName, @Nullable ModelObject producer, ValueConsumer consumer);

        public abstract void beforeMutate(DisplayName displayName);

        public abstract ValueConsumer forUpstream(ValueConsumer consumer);
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
        public boolean shouldFinalize(DisplayName displayName, @Nullable ModelObject producer) {
            if (disallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotFinalizeValueOf(displayName, reason));
                }
            }
            return true;
        }

        @Override
        public FinalizationState<S> finalState() {
            return Cast.uncheckedCast(FINALIZED_VALUE);
        }

        @Override
        public boolean maybeFinalizeOnRead(DisplayName displayName, @Nullable ModelObject producer, ValueConsumer consumer) {
            if (disallowUnsafeRead || consumer == ValueConsumer.DisallowUnsafeRead) {
                String reason = host.beforeRead(producer);
                if (reason != null) {
                    throw new IllegalStateException(cannotQueryValueOf(displayName, reason));
                }
            }
            return finalizeOnNextGet || consumer == ValueConsumer.DisallowUnsafeRead;
        }

        @Override
        public ValueConsumer forUpstream(ValueConsumer consumer) {
            if (disallowUnsafeRead) {
                return ValueConsumer.DisallowUnsafeRead;
            } else {
                return consumer;
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

        private String cannotFinalizeValueOf(DisplayName displayName, String reason) {
            return cannot("finalize", displayName, reason);
        }

        private String cannotQueryValueOf(DisplayName displayName, String reason) {
            return cannot("query", displayName, reason);
        }

        private String cannot(String what, DisplayName displayName, String reason) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot " + what + " the value of ");
            formatter.append(displayName.getDisplayName());
            formatter.append(" because ");
            formatter.append(reason);
            formatter.append(".");
            return formatter.toString();
        }
    }

    private static class FinalizedValue<S> extends FinalizationState<S> {
        @Override
        public boolean shouldFinalize(DisplayName displayName, @Nullable ModelObject producer) {
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
            // Finalized already so read is safe
        }

        @Override
        public boolean maybeFinalizeOnRead(DisplayName displayName, @Nullable ModelObject producer, ValueConsumer consumer) {
            // Already finalized
            return false;
        }

        @Override
        public void beforeMutate(DisplayName displayName) {
            throw new IllegalStateException(String.format("The value for %s is final and cannot be changed any further.", displayName.getDisplayName()));
        }

        @Override
        public ValueConsumer forUpstream(ValueConsumer consumer) {
            throw unexpected();
        }

        @Override
        public S explicitValue(S value) {
            throw unexpected();
        }

        @Override
        public S explicitValue(S value, S defaultValue) {
            throw unexpected();
        }

        @Override
        public S applyConvention(S value, S convention) {
            throw unexpected();
        }

        @Override
        public S implicitValue() {
            throw unexpected();
        }

        @Override
        public FinalizationState<S> finalState() {
            // TODO - it is currently possible for multiple threads to finalize a property instance concurrently (https://github.com/gradle/gradle/issues/12811)
            // This should be strict
            return this;
        }

        @Override
        void setConvention(S convention) {
            throw unexpected();
        }

        private UnsupportedOperationException unexpected() {
            return new UnsupportedOperationException("This property is in an unexpected state.");
        }
    }
}
