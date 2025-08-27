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

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Transformer;
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableSupplier;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;
import org.gradle.internal.DisplayName;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public class Providers {
    private static final NoValueProvider<Object> NULL_PROVIDER = new NoValueProvider<>(ValueSupplier.Value.MISSING);

    public static final Provider<Boolean> TRUE = of(true);
    public static final Provider<Boolean> FALSE = of(false);

    public static <T> ProviderInternal<T> fixedValue(DisplayName owner, T value, Class<T> targetType, ValueSanitizer<T> sanitizer) {
        value = sanitizer.sanitize(value);
        if (!targetType.isInstance(value)) {
            throw new IllegalArgumentException(String.format("Cannot set the value of %s of type %s using an instance of type %s.", owner.getDisplayName(), targetType.getName(), value.getClass().getName()));
        }
        return new FixedValueProvider<>(value);
    }

    public static <T> ProviderInternal<T> nullableValue(ValueSupplier.Value<? extends T> value) {
        if (value.isMissing()) {
            if (value.getPathToOrigin().isEmpty()) {
                return notDefined();
            } else {
                return new NoValueProvider<>(value);
            }
        } else {
            ProviderInternal<T> provider = of(value.getWithoutSideEffect());
            ValueSupplier.SideEffect<?> sideEffect = value.getSideEffect();
            return sideEffect == null ? provider : provider.withSideEffect(Cast.uncheckedCast(sideEffect));
        }
    }

    public static <T> ProviderInternal<T> notDefined() {
        return Cast.uncheckedCast(NULL_PROVIDER);
    }

    public static <T> ProviderInternal<T> of(T value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        return new FixedValueProvider<>(value);
    }

    public static <T extends Named> NamedDomainObjectProvider<T> ofNamed(T value) {
        if (value == null) {
            throw new IllegalArgumentException();
        }
        return new NamedFixedValueProvider<>(value);
    }

    public static <T> ProviderInternal<T> internal(final Provider<T> value) {
        return Cast.uncheckedCast(value);
    }

    public static <T> ProviderInternal<T> ofNullable(@Nullable T value) {
        if (value == null) {
            return notDefined();
        } else {
            return of(value);
        }
    }

    public interface SerializableCallable<V> extends Callable<V>, Serializable {
    }

    public static <T> ProviderInternal<T> changing(SerializableCallable<T> value) {
        return new ChangingProvider<>(value);
    }

    public static <T> ProviderInternal<T> memoizing(ProviderInternal<T> provider) {
        return memoizing(provider, null);
    }

    public static <T> ProviderInternal<T> memoizing(ProviderInternal<T> provider, @Nullable SerializableSupplier<DisplayName> displayName) {
        return new MemoizingProvider<>(provider, displayName);
    }

    private static class MemoizingProvider<T> extends AbstractMinimalProvider<T> {
        private final ProviderInternal<T> provider;
        @Nullable
        private Value<? extends T> value;
        @Nullable
        private final Supplier<DisplayName> displayName;

        public MemoizingProvider(ProviderInternal<T> provider, @Nullable SerializableSupplier<DisplayName> displayName) {
            this.provider = provider;
            this.displayName = displayName;
        }

        @Override
        protected @Nullable DisplayName getDeclaredDisplayName() {
            return displayName != null ? displayName.get() : null;
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            if (value != null) {
                return ExecutionTimeValue.value(value);
            }
            ExecutionTimeValue<? extends T> executionTimeValue = provider.calculateExecutionTimeValue();
            if (executionTimeValue.isMissing()) {
                return executionTimeValue;
            }
            if (executionTimeValue.hasFixedValue()) {
                value = executionTimeValue.toValue();
                return ExecutionTimeValue.value(value);
            }
            return ExecutionTimeValue.changingValue(this);
        }

        @Override
        public ValueProducer getProducer() {
            if (value != null) {
                return ValueProducer.noProducer();
            }
            return provider.getProducer();
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            if (value == null) {
                value = provider.calculateValue(consumer);
            }
            return value;
        }

        @Override
        public @Nullable Class<T> getType() {
            return provider.getType();
        }
    }

    public static class FixedValueProvider<T> extends AbstractProviderWithValue<T> {
        protected final T value;

        FixedValueProvider(T value) {
            this.value = value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return Cast.uncheckedCast(value.getClass());
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            return Value.of(value);
        }

        @Override
        public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
            return this;
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return ExecutionTimeValue.fixedValue(value);
        }

        @Override
        protected String toStringNoReentrance() {
            return String.format("fixed(%s, %s)", getType(), value);
        }
    }

    public static class NamedFixedValueProvider<T extends Named> extends FixedValueProvider<T> implements NamedDomainObjectProvider<T> {
        NamedFixedValueProvider(T value) {
            super(value);
        }

        @Override
        public void configure(Action<? super T> action) {
            action.execute(value);
        }

        @Override
        public String getName() {
            return value.getName();
        }
    }

    public static class FixedValueWithChangingContentProvider<T> extends FixedValueProvider<T> {
        public FixedValueWithChangingContentProvider(T value) {
            super(value);
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return super.calculateExecutionTimeValue().withChangingContent();
        }
    }

    private static class NoValueProvider<T> extends AbstractMinimalProvider<T> {
        private final Value<? extends T> value;

        public NoValueProvider(Value<? extends T> value) {
            assert value.isMissing();
            this.value = value;
        }

        @Override
        public Value<? extends T> calculateValue(ValueConsumer consumer) {
            return value;
        }

        @Override
        public boolean isImmutable() {
            return true;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return null;
        }

        @Override
        protected Value<T> calculateOwnValue(ValueConsumer consumer) {
            return Value.missing();
        }

        @Override
        public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
            return ExecutionTimeValue.missing();
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super T> transformer) {
            return Cast.uncheckedCast(this);
        }

        @Override
        public boolean isPresent() {
            return false;
        }

        @Override
        public boolean calculatePresence(ValueConsumer consumer) {
            return false;
        }

        @Override
        public ProviderInternal<T> asSupplier(DisplayName owner, Class<? super T> targetType, ValueSanitizer<? super T> sanitizer) {
            return this;
        }

        @Override
        public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
            return this;
        }

        @Override
        public Provider<T> orElse(T value) {
            return Providers.of(value);
        }

        @Override
        public Provider<T> orElse(Provider<? extends T> provider) {
            return Cast.uncheckedCast(provider);
        }

        @Override
        protected String toStringNoReentrance() {
            return "undefined";
        }
    }
}
