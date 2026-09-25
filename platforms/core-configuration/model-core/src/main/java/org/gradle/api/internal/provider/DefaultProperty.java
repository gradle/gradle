/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.base.Preconditions;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.gradle.internal.evaluation.EvaluationScopeContext;
import org.gradle.internal.state.ModelObject;
import org.gradle.internal.state.NestedObjectOwner;
import org.gradle.internal.state.OwnerAware;
import org.jspecify.annotations.Nullable;

/**
 * The implementation for general-purpose (atomic, non-composite) properties, where
 * the value is supplied by some provider.
 *
 * @param <T> the type of the property value
 */
public class DefaultProperty<T> extends AbstractProperty<T, ProviderInternal<? extends T>> implements Property<T> {
    private final Class<T> type;
    private final ValueSanitizer<T> sanitizer;
    private final static ProviderInternal<?> NOT_DEFINED = Providers.notDefined();
    @Nullable
    private NestedObjectOwner nestedOwner;
    @Nullable
    private NestedValueOwner<T> nestedValueOwner;
    @Nullable
    private Value<? extends T> nestedValueToFinalize;

    @SuppressWarnings("this-escape")
    public DefaultProperty(PropertyHost propertyHost, Class<T> type) {
        super(propertyHost);
        this.type = type;
        this.sanitizer = ValueSanitizers.forType(type);
        init(getDefaultValue());
    }

    @Override
    protected ProviderInternal<? extends T> getDefaultValue() {
        return Providers.notDefined();
    }

    @Override
    public Object unpackState() {
        return getProvider();
    }

    @Override
    public Class<?> publicType() {
        return Property.class;
    }

    @Override
    public int getFactoryId() {
        return ManagedFactories.PropertyManagedFactory.FACTORY_ID;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public void setFromAnyValue(Object object) {
        if (object instanceof Provider) {
            set(Cast.<Provider<T>>uncheckedNonnullCast(object));
        } else {
            set(Cast.<T>uncheckedNonnullCast(object));
        }
    }

    @Override
    public void set(@Nullable T value) {
        if (value == null) {
            discardValue();
        } else {
            setSupplier(Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer));
        }
    }

    @Override
    public Property<T> value(@Nullable T value) {
        set(value);
        return this;
    }

    @Override
    public Property<T> value(Provider<? extends T> provider) {
        set(provider);
        return this;
    }

    public ProviderInternal<? extends T> getProvider() {
        // TODO(mlopatkin) while calling getProvider is not going to cause StackOverflowError by itself, the returned provider is typically used in some recursive call.
        //  Without the safety net of the EvaluationContext, it can cause hard-to-debug exceptions.
        try (EvaluationScopeContext context = openScope()) {
            return getSupplier(context);
        }
    }

    /**
     * Associates the values of this nested declaration with their enclosing model object.
     * This does not query the value or make the enclosing object a producer of this property.
     */
    public void attachNestedOwner(ModelObject owner, DisplayName displayName) {
        attachOwner(owner, displayName);
        if (nestedOwner == null) {
            nestedOwner = new NestedObjectOwner(owner, displayName);
        } else {
            nestedOwner.addOwner(owner);
        }
    }

    /**
     * Returns the provider to serialize, retaining the value attachment performed by nested declarations.
     */
    public ProviderInternal<? extends T> getProviderForSerialization() {
        return nestedOwner == null ? getProvider() : this;
    }

    private void attachNestedValue(ProviderInternal<? extends T> supplier, Value<? extends T> value) {
        if (nestedOwner != null && !value.isMissing() && value.getWithoutSideEffect() instanceof OwnerAware) {
            NestedValueOwner<T> binding = nestedValueOwner;
            if (binding == null || binding.supplier != supplier || binding.value.getWithoutSideEffect() != value.getWithoutSideEffect()) {
                binding = new NestedValueOwner<>(this, nestedOwner, supplier, value, getDisplayName());
                nestedValueOwner = binding;
            }
            ((OwnerAware) value.getWithoutSideEffect()).attachOwner(binding.context, getDisplayName());
        }
    }

    private boolean claimNestedValue(NestedValueOwner<T> binding) {
        if (nestedValueOwner != binding || getProvider() != binding.supplier) {
            return false;
        }
        if (!isFinalized()) {
            nestedValueToFinalize = binding.value;
            try {
                finalizeValue();
                binding.supplier = getProvider();
            } finally {
                nestedValueToFinalize = null;
            }
        }
        return true;
    }

    public DefaultProperty<T> provider(Provider<? extends T> provider) {
        set(provider);
        return this;
    }

    @Override
    public void set(Provider<? extends T> provider) {
        Preconditions.checkArgument(provider != null, "Cannot set the value of a property using a null provider.");
        ProviderInternal<? extends T> p = Providers.internal(provider);
        setSupplier(p.asSupplier(getValidationDisplayName(), type, sanitizer));
    }

    @Override
    public Property<T> convention(@Nullable T value) {
        if (value == null) {
            setConvention(Providers.notDefined());
        } else {
            setConvention(Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer));
        }
        return this;
    }

    @Override
    public Property<T> convention(Provider<? extends T> provider) {
        Preconditions.checkArgument(provider != null, "Cannot set the convention of a property using a null provider.");
        setConvention(Providers.internal(provider).asSupplier(getValidationDisplayName(), type, sanitizer));
        return this;
    }

    @Override
    public Property<T> unset() {
        super.unset();
        return this;
    }

    @Override
    public Property<T> unsetConvention() {
        discardConvention();
        return this;
    }

    @Override
    protected ExecutionTimeValue<? extends T> calculateOwnExecutionTimeValue(EvaluationScopeContext context, ProviderInternal<? extends T> value) {
        ExecutionTimeValue<? extends T> result = value.calculateExecutionTimeValue();
        if (result.hasFixedValue() && value == getSupplier(context)) {
            attachNestedValue(value, result.toValue());
        }
        return result;
    }

    @Override
    protected Value<? extends T> calculateValueFrom(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        Value<? extends T> result = value.calculateValue(consumer);
        if (value == getSupplier(context)) {
            attachNestedValue(value, result);
        }
        return result;
    }

    @Override
    protected ProviderInternal<? extends T> finalValue(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        ProviderInternal<? extends T> result;
        if (nestedValueToFinalize != null) {
            ValueProducer producer = value.getProducer();
            producer.visitContentProducerTasks(task -> {
                throw new IllegalStateException("Cannot infer output ownership for " + getDisplayName()
                    + " because its structure is produced by " + task + ". Configure the output bean before task execution.");
            });
            result = new FixedNestedValue<>(nestedValueToFinalize, producer);
        } else {
            result = value.withFinalValue(consumer);
        }
        if (nestedOwner != null) {
            Value<? extends T> fixedValue = result.calculateValue(consumer);
            NestedValueOwner<T> binding = nestedValueOwner;
            if (binding != null && !fixedValue.isMissing() && binding.value.getWithoutSideEffect() == fixedValue.getWithoutSideEffect()) {
                binding.supplier = result;
            } else {
                attachNestedValue(result, fixedValue);
            }
        }
        return result;
    }

    @Override
    protected ProviderInternal<? extends T> getDefaultConvention() {
        return Cast.uncheckedCast(NOT_DEFINED);
    }

    @Override
    protected boolean isDefaultConvention() {
        return getConventionSupplier() == NOT_DEFINED;
    }

    @Override
    protected String describeContents() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("property(%s, %s)", type.getName(), describeValue());
    }

    public void replace(Transformer<? extends @Nullable Provider<? extends T>, ? super Provider<T>> transformation) {
        Provider<? extends T> newValue = transformation.transform(shallowCopy());
        if (newValue != null) {
            set(newValue);
        } else {
            set((T) null);
        }
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
        private final ModelObject enclosing;
        private ProviderInternal<? extends T> supplier;
        private final Value<? extends T> value;
        private final DisplayName displayName;
        private final NestedObjectOwner context;

        private NestedValueOwner(DefaultProperty<T> property, ModelObject enclosing, ProviderInternal<? extends T> supplier, Value<? extends T> value, DisplayName displayName) {
            this.property = property;
            this.enclosing = enclosing;
            this.supplier = supplier;
            this.value = value;
            this.displayName = displayName;
            this.context = new NestedObjectOwner(this, displayName);
        }

        @Override
        @Nullable
        public Task getTaskThatOwnsThisObject() {
            return property.claimNestedValue(this) ? enclosing.getTaskThatOwnsThisObject() : null;
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
