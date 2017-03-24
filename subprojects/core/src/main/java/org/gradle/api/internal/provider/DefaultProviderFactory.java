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

        return new DefaultProvider(value);
    }

    @Override
    public <T> PropertyState<T> property(Class<T> clazz) {
        if (clazz == null) {
            throw new InvalidUserDataException("Class cannot be null");
        }

        PropertyState<T> propertyState = new DefaultPropertyState<T>();

        if (clazz == Boolean.class) {
            ((PropertyState<Boolean>) propertyState).set(Boolean.FALSE);
        } else if (clazz == Byte.class) {
            ((PropertyState<Byte>) propertyState).set(Byte.valueOf((byte) 0));
        } else if (clazz == Short.class) {
            ((PropertyState<Short>) propertyState).set(Short.valueOf((short) 0));
        } else if (clazz == Integer.class) {
            ((PropertyState<Integer>) propertyState).set(Integer.valueOf(0));
        } else if (clazz == Long.class) {
            ((PropertyState<Long>) propertyState).set(Long.valueOf(0));
        } else if (clazz == Float.class) {
            ((PropertyState<Float>) propertyState).set(Float.valueOf(0));
        } else if (clazz == Double.class) {
            ((PropertyState<Double>) propertyState).set(Double.valueOf(0));
        } else if (clazz == Character.class) {
            ((PropertyState<Character>) propertyState).set(new Character('\0'));
        }

        return propertyState;
    }
}
