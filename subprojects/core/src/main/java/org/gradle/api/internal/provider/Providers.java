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

import org.gradle.api.provider.Provider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;

import static org.gradle.api.internal.provider.AbstractProvider.NON_NULL_VALUE_EXCEPTION_MESSAGE;

public class Providers {
    private static final Provider<Object> NULL_PROVIDER = new ProviderInternal<Object>() {
        @Override
        public Object get() {
            throw new IllegalStateException(NON_NULL_VALUE_EXCEPTION_MESSAGE);
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
        return new AbstractProvider<T>() {
            @Nullable
            @Override
            public Class<T> getType() {
                return Cast.uncheckedCast(value.getClass());
            }

            @Override
            public T getOrNull() {
                return value;
            }
        };
    }
}
