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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.internal.DisplayName;
import org.gradle.internal.logging.text.TreeFormatter;

import javax.annotation.Nullable;

public class DefaultProperty<T> extends AbstractProperty<T> implements Property<T> {
    private final Class<T> type;
    private final ValueSanitizer<T> sanitizer;
    private ScalarSupplier<? extends T> convention = Providers.noValue();
    private ScalarSupplier<? extends T> valueSupplier;

    public DefaultProperty(Class<T> type) {
        applyDefaultValue();
        this.type = type;
        this.sanitizer = ValueSanitizers.forType(type);
    }

    @Override
    protected ValueSupplier getSupplier() {
        return valueSupplier;
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
            set((Provider<T>) object);
        } else {
            set((T) object);
        }
    }

    @Override
    public void set(T value) {
        if (value == null) {
            if (beforeReset()) {
                this.valueSupplier = convention;
            }
            return;
        }

        if (beforeMutate()) {
            this.valueSupplier = Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer);
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
        return valueSupplier.asProvider();
    }

    public DefaultProperty<T> provider(Provider<? extends T> provider) {
        set(provider);
        return this;
    }

    @Override
    public void set(Provider<? extends T> provider) {
        if (!beforeMutate()) {
            return;
        }
        if (provider == null) {
            throw new IllegalArgumentException("Cannot set the value of a property using a null provider.");
        }
        ProviderInternal<? extends T> p = Providers.internal(provider);
        this.valueSupplier = p.asSupplier(getValidationDisplayName(), type, sanitizer);
    }

    @Override
    public Property<T> convention(T value) {
        if (value == null) {
            applyConvention(Providers.noValue());
        } else {
            applyConvention(Providers.fixedValue(getValidationDisplayName(), value, type, sanitizer));
        }
        return this;
    }

    @Override
    public Property<T> convention(Provider<? extends T> valueProvider) {
        ProviderInternal<? extends T> providerInternal = Providers.internal(valueProvider);
        ScalarSupplier<? extends T> conventionSupplier = providerInternal.asSupplier(getValidationDisplayName(), type, sanitizer);
        applyConvention(conventionSupplier);
        return this;
    }

    private void applyConvention(ScalarSupplier<? extends T> conventionSupplier) {
        if (shouldApplyConvention()) {
            this.valueSupplier = conventionSupplier;
        }
        this.convention = conventionSupplier;
    }

    @Override
    protected void applyDefaultValue() {
        valueSupplier = Providers.noValue();
    }

    @Override
    protected void makeFinal() {
        valueSupplier = valueSupplier.withFinalValue();
        convention = Providers.noValue();
    }

    @Override
    public T get() {
        beforeRead();
        Value<? extends T> value = valueSupplier.calculateValue();
        if (value.isMissing()) {
            TreeFormatter formatter = new TreeFormatter();
            formatter.node("Cannot query the value of ").append(getDisplayName().getDisplayName()).append(" because it has no value available.");
            if (!value.getPathToOrigin().isEmpty()) {
                formatter.node("The value of this property is derived from");
                formatter.startChildren();
                for (DisplayName displayName : value.getPathToOrigin()) {
                    formatter.node(displayName.getDisplayName());
                }
                formatter.endChildren();
            }
            throw new MissingValueException(formatter.toString());
        }
        return value.get();
    }

    @Override
    public Value<? extends T> calculateValue() {
        beforeRead();
        return valueSupplier.calculateValue().pushWhenMissing(getDisplayName());
    }

    @Override
    public T getOrNull() {
        beforeRead();
        return valueSupplier.calculateValue().orNull();
    }

    @Override
    public T getOrElse(T defaultValue) {
        beforeRead();
        return valueSupplier.calculateValue().orElse(defaultValue);
    }

    @Override
    public boolean isPresent() {
        beforeRead();
        return valueSupplier.isPresent();
    }

    @Override
    protected String describeContents() {
        // NOTE: Do not realize the value of the Provider in toString().  The debugger will try to call this method and make debugging really frustrating.
        return String.format("property(%s, %s)", type, valueSupplier);
    }
}
