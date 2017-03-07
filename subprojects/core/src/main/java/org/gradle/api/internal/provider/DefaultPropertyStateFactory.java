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
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.provider.ConfigurablePropertyState;
import org.gradle.api.provider.PropertyStateFactory;

public class DefaultPropertyStateFactory implements PropertyStateFactory {

    private final TaskResolver taskResolver;

    public DefaultPropertyStateFactory() {
        this(null);
    }

    public DefaultPropertyStateFactory(TaskResolver taskResolver) {
        this.taskResolver = taskResolver;
    }

    @Override
    public <T> ConfigurablePropertyState<T> property(Class<T> clazz) {
        if (clazz == null) {
            throw new InvalidUserDataException("Class cannot be null");
        }

        ConfigurablePropertyState<T> propertyState = new DefaultConfigurablePropertyState<T>(taskResolver);

        if (clazz == Boolean.class) {
            ((ConfigurablePropertyState<Boolean>)propertyState).set(Boolean.FALSE);
        } else if (clazz == Byte.class) {
            ((ConfigurablePropertyState<Byte>)propertyState).set(Byte.valueOf((byte) 0));
        } else if (clazz == Short.class) {
            ((ConfigurablePropertyState<Short>)propertyState).set(Short.valueOf((short) 0));
        } else if (clazz == Integer.class) {
            ((ConfigurablePropertyState<Integer>)propertyState).set(Integer.valueOf(0));
        } else if (clazz == Long.class) {
            ((ConfigurablePropertyState<Long>)propertyState).set(Long.valueOf(0));
        } else if (clazz == Float.class) {
            ((ConfigurablePropertyState<Float>)propertyState).set(Float.valueOf(0));
        } else if (clazz == Double.class) {
            ((ConfigurablePropertyState<Double>)propertyState).set(Double.valueOf(0));
        } else if (clazz == Character.class) {
            ((ConfigurablePropertyState<Character>)propertyState).set(new Character('\0'));
        }

        return propertyState;
    }
}
