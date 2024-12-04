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
import org.gradle.api.Transformer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.evaluation.EvaluationScopeContext;

import javax.annotation.Nullable;

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
        // Discard this property from a provider chain, as it does not contribute anything to the calculation.
        return value.calculateExecutionTimeValue();
    }

    @Override
    protected Value<? extends T> calculateValueFrom(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        return value.calculateValue(consumer);
    }

    @Override
    protected ProviderInternal<? extends T> finalValue(EvaluationScopeContext context, ProviderInternal<? extends T> value, ValueConsumer consumer) {
        return value.withFinalValue(consumer);
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

    public void replace(Transformer<? extends @org.jetbrains.annotations.Nullable Provider<? extends T>, ? super Provider<T>> transformation) {
        Provider<? extends T> newValue = transformation.transform(shallowCopy());
        if (newValue != null) {
            set(newValue);
        } else {
            set((T) null);
        }
    }
}
