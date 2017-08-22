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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.provider.PropertyState;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import java.util.concurrent.Callable;

public class DefaultProviderFactory implements ProviderFactory {

    public <T> Provider<T> provider(final Callable<T> value) {
        if (value == null) {
            throw new InvalidUserDataException("Value cannot be null");
        }

        return new DefaultProvider<T>(value);
    }

    @Override
    public <T> PropertyState<T> property(Class<T> type) {
        if (type == null) {
            throw new InvalidUserDataException("Class cannot be null");
        }

        PropertyState<T> propertyState = new DefaultPropertyState<T>(type);

        if (type == Boolean.class) {
            ((PropertyState<Boolean>) propertyState).set(Providers.FALSE);
        } else if (type == Byte.class) {
            ((PropertyState<Byte>) propertyState).set(Providers.BYTE_ZERO);
        } else if (type == Short.class) {
            ((PropertyState<Short>) propertyState).set(Providers.SHORT_ZERO);
        } else if (type == Integer.class) {
            ((PropertyState<Integer>) propertyState).set(Providers.INTEGER_ZERO);
        } else if (type == Long.class) {
            ((PropertyState<Long>) propertyState).set(Providers.LONG_ZERO);
        } else if (type == Float.class) {
            ((PropertyState<Float>) propertyState).set(Providers.FLOAT_ZERO);
        } else if (type == Double.class) {
            ((PropertyState<Double>) propertyState).set(Providers.DOUBLE_ZERO);
        } else if (type == Character.class) {
            ((PropertyState<Character>) propertyState).set(Providers.CHAR_ZERO);
        }

        return propertyState;
    }
}
