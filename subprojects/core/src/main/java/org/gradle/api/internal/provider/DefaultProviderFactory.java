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
import org.gradle.api.provider.ProviderFactory;

import java.util.concurrent.Callable;

public class DefaultProviderFactory implements ProviderFactory {

    public <T> Provider<T> provider(final Callable<? extends T> value) {
        if (value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }

        return new DefaultProvider<T>(value);
    }

    // This should be extracted out
    public <T> Property<T> propertyNoNag(Class<T> valueType) {
        if (valueType == null) {
            throw new IllegalArgumentException("Class cannot be null");
        }

        Property<T> property = new DefaultPropertyState<T>(valueType);

        if (valueType == Boolean.class) {
            ((Property<Boolean>) property).set(Providers.FALSE);
        } else if (valueType == Byte.class) {
            ((Property<Byte>) property).set(Providers.BYTE_ZERO);
        } else if (valueType == Short.class) {
            ((Property<Short>) property).set(Providers.SHORT_ZERO);
        } else if (valueType == Integer.class) {
            ((Property<Integer>) property).set(Providers.INTEGER_ZERO);
        } else if (valueType == Long.class) {
            ((Property<Long>) property).set(Providers.LONG_ZERO);
        } else if (valueType == Float.class) {
            ((Property<Float>) property).set(Providers.FLOAT_ZERO);
        } else if (valueType == Double.class) {
            ((Property<Double>) property).set(Providers.DOUBLE_ZERO);
        } else if (valueType == Character.class) {
            ((Property<Character>) property).set(Providers.CHAR_ZERO);
        }

        return property;
    }
}
