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
import org.gradle.api.provider.SupportsConvention;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.state.ModelObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The base implementation for all properties in Gradle.
 * <p>
 *     A property is a provider where the value is configurable.
 * </p>
 * <p>
 *     A property's value is not stored in the property itself,
 *     but computed by some {@link ValueSupplier}, which
 *     provides the basic machinery for lazy evaluation.
 * </p>
 *
 * @param <T> the type of the value this property provides
 * @param <S> the type of value supplier that actually provides the value for this property
 */
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

    @Override
    public boolean isFinalized() {
        return state.isFinalized();
    }

    protected boolean isExplicit() {
        return state.isExplicit();
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context, consumer); // may throw its own exception, which should not be wrapped.
            try {
                return getSupplier(context).calculatePresence(consumer);
            } catch (Exception e) {
                if (displayName != null) {
                    throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
                } else {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
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

    protected final S getSupplier(@SuppressWarnings("unused") EvaluationScopeContext context) {
        // context serves as a token here to ensure that the scope is opened.
        return value;
    }

    protected S getConventionSupplier() {
        return state.convention();
    }

    protected final String describeValue() {
        return value.toString();
    }

    protected Value<? extends T> calculateOwnValueNoProducer(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeReadNoProducer(context, consumer);
            return doCalculateValue(context, consumer);
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        try (EvaluationScopeContext context = openScope()) {
            beforeRead(context, consumer);
            return doCalculateValue(context, consumer);
        }
    }

    @Nonnull
    private Value<? extends T> doCalculateValue(EvaluationScopeContext context, ValueConsumer consumer) {
        try {
            return calculateValueFrom(context, value, consumer);
        } catch (Exception e) {
            if (displayName != null) {
                throw new PropertyQueryException(String.format("Failed to query the value of %s.", displayName), e);
            } else {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    protected abstract Value<? extends T> calculateValueFrom(EvaluationScopeContext context, S value, ValueConsumer consumer);

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        try (EvaluationScopeContext context = openScope()) {
            ExecutionTimeValue<? extends T> value = calculateOwnExecutionTimeValue(context, this.value);
            if (getProducerTask() == null) {
                return value;
            } else {
                return value.withChangingContent();
            }
        }
    }

    protected abstract ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue(EvaluationScopeContext context, S value);

    /**
     * Returns a diagnostic string describing the current source of value of this property. Should not realize the value.
     */
    protected abstract String describeContents();

    // This method is final - implement describeContents() instead
    @Override
    protected final String toStringNoReentrance() {
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
            try (EvaluationScopeContext context = openScope()) {
                return getSupplier(context).getProducer();
            }
        }
    }

    @Override
    public void finalizeValue() {
        if (state.shouldFinalize(this.getDisplayName(), producer)) {
            try (EvaluationScopeContext context = openScope()) {
                finalizeNow(context, ValueConsumer.IgnoreUnsafeRead);
            }
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
        if (state.isUpgradedPropertyValue()) {
            // Upgraded properties should not be finalized to simplify migration.
            // This behaviour should be removed with Gradle 10.
            state.warnOnUpgradedPropertyValueChanges();
        } else {
            state.disallowChangesAndFinalizeOnNextGet();
        }
    }

    @Override
    public void markAsUpgradedProperty() {
        state.markAsUpgradedPropertyValue();
    }

    @Override
    public void disallowUnsafeRead() {
        state.disallowUnsafeRead();
    }

    protected abstract S finalValue(EvaluationScopeContext context, S value, ValueConsumer consumer);

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
    protected void beforeRead(EvaluationScopeContext context, ValueConsumer consumer) {
        beforeRead(context, producer, consumer);
    }

    protected void beforeReadNoProducer(EvaluationScopeContext context, ValueConsumer consumer) {
        beforeRead(context, null, consumer);
    }

    private void beforeRead(EvaluationScopeContext context, @Nullable ModelObject effectiveProducer, ValueConsumer consumer) {
        state.finalizeOnReadIfNeeded(this.getDisplayName(), effectiveProducer, consumer, effectiveConsumer -> finalizeNow(context, effectiveConsumer));
    }

    private void finalizeNow(EvaluationScopeContext context, ValueConsumer consumer) {
        try {
            value = finalValue(context, value, state.forUpstream(consumer));
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
        if (isDefaultConvention()) {
            // special case: discarding value without a convention restores the initial state
            state.implicitValue(getDefaultConvention());
            value = getDefaultValue();
        } else {
            // otherwise, the convention will become the new value
            value = state.implicitValue(state.convention());
        }
    }

    /**
     * Discards the convention of this property.
     */
    protected void discardConvention() {
        assertCanMutate();
        value = state.applyConvention(value, getDefaultConvention());
    }

    @Override
    public SupportsConvention unsetConvention() {
        discardConvention();
        return this;
    }

    @Override
    public SupportsConvention unset() {
        discardValue();
        return this;
    }

    /**
     * Sets the value of the property to the current convention value, replacing whatever explicit value the property already had.
     *
     * If the property has no convention set at the time this method is invoked,
     * the effect of invoking it is similar to invoking {@link #unset()}.
     */
    protected SupportsConvention setToConvention() {
        assertCanMutate();
        this.value = state.setToConvention();
        return this;
    }

    /**
     * Sets the value of the property to the current convention value, if an explicit
     * value has not been set yet.
     *
     * If the property has no convention set at the time this method is invoked,
     * or if an explicit value has already been set, it has no effect.
     */
    protected SupportsConvention setToConventionIfUnset() {
        assertCanMutate();
        if (!isDefaultConvention()) {
            this.value = state.setToConventionIfUnset(value);
        }
        return this;
    }

    protected abstract S getDefaultValue();

    protected abstract S getDefaultConvention();

    /**
     * Is convention set to the initial convention value?
     */
    protected abstract boolean isDefaultConvention();

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

    /**
     * Creates a shallow copy of this property. Further changes to this property (via {@code set(...)}, or {@code convention(Object...)}) do not
     * change the copy. However, the copy still reflects changes to the underlying providers that constitute this property. Consider the following snippet:
     * <pre>
     *     def upstream = objects.property(String).value("foo")
     *     def property = objects.property(String).value(upstream)
     *     def copy = property.shallowCopy()
     *     property.set("bar")  // does not affect contents of the copy
     *     upstream.set("qux")  // does affect the content of the copy
     *
     *     println(copy.get())  // prints qux
     * </pre>
     * <p>
     * The copy doesn't share the producer of this property, but inherits producers of the current property value.
     *
     * @return the shallow copy of this property
     */
    public ProviderInternal<T> shallowCopy() {
        return new ShallowCopyProvider();
    }

    private class ShallowCopyProvider extends AbstractMinimalProvider<T> {
        // the value of "value" is immutable but the field is not, so copy it
        // (but use a different owner)
        private final S copiedValue = value;

        @Override
        public ValueProducer getProducer() {
            try (EvaluationScopeContext ignored = openScope()) {
                return copiedValue.getProducer();
            }
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            try (EvaluationScopeContext context = openScope()) {
                return calculateOwnExecutionTimeValue(context, copiedValue);
            }
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            try (EvaluationScopeContext context = openScope()) {
                return calculateValueFrom(context, copiedValue, consumer);
            }
        }

        @Override
        @Nullable
        public Class<T> getType() {
            return AbstractProperty.this.getType();
        }
    }
}
