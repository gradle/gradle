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

import org.gradle.api.Transformer;
import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

public class Providers {
    public static final String NULL_TRANSFORMER_RESULT = "Transformer for this provider returned a null value.";
    public static final String NULL_VALUE = "No value has been specified for this provider.";

    private static final Provider<Object> NULL_PROVIDER = new ProviderInternal<Object>() {
        @Override
        public Object get() {
            throw new IllegalStateException(NULL_VALUE);
        }

        @Nullable
        @Override
        public Class<Object> getType() {
            return null;
        }

        @Override
        public Object getOrNull() {
            return null;
        }

        @Override
        public Object getOrElse(Object defaultValue) {
            return defaultValue;
        }

        @Override
        public <S> ProviderInternal<S> map(Transformer<? extends S, ? super Object> transformer) {
            return Cast.uncheckedCast(this);
        }

        @Override
        public boolean isPresent() {
            return false;
        }
    };

    public static final Provider<Boolean> TRUE = of(true);
    public static final Provider<Boolean> FALSE = of(false);
    public static final Provider<Character> CHAR_ZERO = of((char) 0);
    public static final Provider<Byte> BYTE_ZERO = of((byte) 0);
    public static final Provider<Short> SHORT_ZERO = of((short) 0);
    public static final Provider<Integer> INTEGER_ZERO = of(0);
    public static final Provider<Long> LONG_ZERO = of(0L);
    public static final Provider<Float> FLOAT_ZERO = of(0f);
    public static final Provider<Double> DOUBLE_ZERO = of(0d);

    public static <T> Provider<T> notDefined() {
        return Cast.uncheckedCast(NULL_PROVIDER);
    }

    public static <T> Provider<T> of(final T value) {
        return new FixedValueProvider<T>(value);
    }

    private static class FixedValueProvider<T> implements ProviderInternal<T> {
        private final T value;

        FixedValueProvider(T value) {
            this.value = value;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return Cast.uncheckedCast(value.getClass());
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public T getOrElse(T defaultValue) {
            return value;
        }

        @Override
        public T getOrNull() {
            return value;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public <S> ProviderInternal<S> map(final Transformer<? extends S, ? super T> transformer) {
            return new MappedFixedValueProvider<S, T>(transformer, this);
        }
    }

    private static class MappedFixedValueProvider<S, T> implements ProviderInternal<S> {
        private final Transformer<? extends S, ? super T> transformer;
        private final Provider<T> provider;
        private S value;

        MappedFixedValueProvider(Transformer<? extends S, ? super T> transformer, Provider<T> provider) {
            this.transformer = transformer;
            this.provider = provider;
        }

        @Nullable
        @Override
        public Class<S> getType() {
            if (value != null) {
                return Cast.uncheckedCast(value.getClass());
            }
            return null;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Override
        public S get() {
            if (value == null) {
                value = transformer.transform(provider.get());
                if (value == null) {
                    throw new IllegalStateException(NULL_TRANSFORMER_RESULT);
                }
            }
            return value;
        }

        @Override
        public S getOrElse(S defaultValue) {
            return get();
        }

        @Nullable
        @Override
        public S getOrNull() {
            return get();
        }

        @Override
        public <U> ProviderInternal<U> map(Transformer<? extends U, ? super S> transformer) {
            return new MappedFixedValueProvider<U, S>(transformer, this);
        }
    }
}
