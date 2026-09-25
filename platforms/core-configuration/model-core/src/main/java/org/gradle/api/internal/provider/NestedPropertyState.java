/*
 * Copyright 2026 Gradle and contributors.
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

import org.gradle.api.Describable;
import org.gradle.api.Task;
import org.gradle.api.internal.provider.ValueSupplier.Value;
import org.gradle.api.internal.provider.ValueSupplier.ValueConsumer;
import org.gradle.api.internal.provider.ValueSupplier.ValueProducer;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.NestedObjectOwner;
import org.gradle.internal.state.OwnerAware;
import org.jspecify.annotations.Nullable;

/**
 * Lifecycle state for a scalar nested declaration. Allocated only when a property is attached
 * as a nested declaration, so ordinary properties and their lifecycle states remain unchanged.
 *
 * <p>The original state handles conventions and finalization. Keeping it as a delegate preserves
 * configuration performed before attachment, including an already finalized value.</p>
 */
final class NestedPropertyState<T> extends ValueState<ProviderInternal<? extends T>> {
    private ValueState<ProviderInternal<? extends T>> delegate;
    private final NestedObjectOwner nestedOwner;
    @Nullable
    private NestedValueOwner<T> nestedValueOwner;
    @Nullable
    private Value<? extends T> nestedValueToFinalize;

    NestedPropertyState(ValueState<ProviderInternal<? extends T>> delegate, ModelObject owner, DisplayName displayName) {
        this.delegate = delegate;
        this.nestedOwner = new NestedObjectOwner(owner, displayName);
    }

    void attachOwner(ModelObject owner) {
        nestedOwner.addOwner(owner);
    }

    void attachValue(DefaultProperty<T> property, ProviderInternal<? extends T> supplier, Value<? extends T> value) {
        if (!value.isMissing() && value.getWithoutSideEffect() instanceof OwnerAware) {
            NestedValueOwner<T> binding = nestedValueOwner;
            if (binding == null || binding.supplier != supplier || binding.value.getWithoutSideEffect() != value.getWithoutSideEffect()) {
                binding = new NestedValueOwner<>(property, this, supplier, value, property.getDisplayName());
                nestedValueOwner = binding;
            }
            ((OwnerAware) value.getWithoutSideEffect()).attachOwner(binding.context, property.getDisplayName());
        }
    }

    private boolean claimNestedValue(NestedValueOwner<T> binding) {
        DefaultProperty<T> property = binding.property;
        if (nestedValueOwner != binding || property.getProvider() != binding.supplier) {
            return false;
        }
        if (!property.isFinalized()) {
            nestedValueToFinalize = binding.value;
            try {
                property.finalizeValue();
                binding.supplier = property.getProvider();
            } finally {
                nestedValueToFinalize = null;
            }
        }
        return true;
    }

    ProviderInternal<? extends T> finalValue(DefaultProperty<T> property, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        ProviderInternal<? extends T> result;
        if (nestedValueToFinalize != null) {
            ValueProducer producer = value.getProducer();
            producer.visitContentProducerTasks(task -> {
                throw new IllegalStateException("Cannot infer output ownership for " + property.getDisplayName()
                    + " because its structure is produced by " + task + ". Configure the output bean before task execution.");
            });
            result = new FixedNestedValue<>(nestedValueToFinalize, producer);
        } else {
            result = value.withFinalValue(consumer);
        }
        Value<? extends T> fixedValue = result.calculateValue(consumer);
        NestedValueOwner<T> binding = nestedValueOwner;
        if (binding != null && !fixedValue.isMissing() && binding.value.getWithoutSideEffect() == fixedValue.getWithoutSideEffect()) {
            binding.supplier = result;
        } else {
            attachValue(property, result, fixedValue);
        }
        return result;
    }

    @Override
    public ValueState<ProviderInternal<? extends T>> finalState() {
        delegate = delegate.finalState();
        return this;
    }

    @Override
    public boolean shouldFinalize(Describable displayName, @Nullable ModelObject producer) {
        return delegate.shouldFinalize(displayName, producer);
    }

    @Override
    public void setConvention(ProviderInternal<? extends T> convention) {
        delegate.setConvention(convention);
    }

    @Override
    public void disallowChanges() {
        delegate.disallowChanges();
    }

    @Override
    public boolean isDisallowChanges() {
        return delegate.isDisallowChanges();
    }

    @Override
    public void finalizeOnNextGet() {
        delegate.finalizeOnNextGet();
    }

    @Override
    public void disallowUnsafeRead() {
        delegate.disallowUnsafeRead();
    }

    @Override
    public ProviderInternal<? extends T> explicitValue(ProviderInternal<? extends T> value) {
        return delegate.explicitValue(value);
    }

    @Override
    public ProviderInternal<? extends T> explicitValue(ProviderInternal<? extends T> value, ProviderInternal<? extends T> defaultValue) {
        return delegate.explicitValue(value, defaultValue);
    }

    @Override
    public ProviderInternal<? extends T> applyConvention(ProviderInternal<? extends T> value, ProviderInternal<? extends T> convention) {
        return delegate.applyConvention(value, convention);
    }

    @Override
    public ProviderInternal<? extends T> implicitValue(ProviderInternal<? extends T> convention) {
        return delegate.implicitValue(convention);
    }

    @Override
    public ProviderInternal<? extends T> implicitValue() {
        return delegate.implicitValue();
    }

    @Override
    public boolean maybeFinalizeOnRead(Describable displayName, @Nullable ModelObject producer, ValueConsumer consumer) {
        return delegate.maybeFinalizeOnRead(displayName, producer, consumer);
    }

    @Override
    public void beforeMutate(Describable displayName) {
        delegate.beforeMutate(displayName);
    }

    @Override
    public ValueConsumer forUpstream(ValueConsumer consumer) {
        return delegate.forUpstream(consumer);
    }

    @Override
    public boolean isFinalized() {
        return delegate.isFinalized();
    }

    @Override
    public boolean isFinalizing() {
        return delegate.isFinalizing();
    }

    @Override
    public boolean isExplicit() {
        return delegate.isExplicit();
    }

    @Override
    public ProviderInternal<? extends T> convention() {
        return delegate.convention();
    }

    @Override
    public ProviderInternal<? extends T> setToConvention() {
        return delegate.setToConvention();
    }

    @Override
    public ProviderInternal<? extends T> setToConventionIfUnset(ProviderInternal<? extends T> value) {
        return delegate.setToConventionIfUnset(value);
    }

    @Override
    public void markAsUpgradedPropertyValue() {
        delegate.markAsUpgradedPropertyValue();
    }

    @Override
    public boolean isUpgradedPropertyValue() {
        return delegate.isUpgradedPropertyValue();
    }

    @Override
    public void warnOnUpgradedPropertyValueChanges() {
        delegate.warnOnUpgradedPropertyValueChanges();
    }

    private static class FixedNestedValue<T> extends AbstractMinimalProvider<T> {
        private final Value<? extends T> value;
        private final ValueProducer producer;

        private FixedNestedValue(Value<? extends T> value, ValueProducer producer) {
            this.value = value;
            this.producer = producer;
        }

        @Override
        public ValueProducer getProducer() {
            return producer;
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return value;
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return ExecutionTimeValue.value(value);
        }

        @Override
        public Class<T> getType() {
            return Cast.uncheckedCast(value.getWithoutSideEffect().getClass());
        }
    }

    private static class NestedValueOwner<T> implements ModelObject {
        private final DefaultProperty<T> property;
        private final NestedPropertyState<T> state;
        private ProviderInternal<? extends T> supplier;
        private final Value<? extends T> value;
        private final DisplayName displayName;
        private final NestedObjectOwner context;

        private NestedValueOwner(DefaultProperty<T> property, NestedPropertyState<T> state, ProviderInternal<? extends T> supplier, Value<? extends T> value, DisplayName displayName) {
            this.property = property;
            this.state = state;
            this.supplier = supplier;
            this.value = value;
            this.displayName = displayName;
            this.context = new NestedObjectOwner(this, displayName);
        }

        @Override
        @Nullable
        public Task getTaskThatOwnsThisObject() {
            return state.claimNestedValue(this) ? state.nestedOwner.getTaskThatOwnsThisObject() : null;
        }

        @Override
        public DisplayName getModelIdentityDisplayName() {
            return displayName;
        }

        @Override
        public boolean hasUsefulDisplayName() {
            return true;
        }

        @Override
        public void attachModelProperties() {
        }

        @Override
        public String toString() {
            return displayName.getDisplayName();
        }
    }
}
