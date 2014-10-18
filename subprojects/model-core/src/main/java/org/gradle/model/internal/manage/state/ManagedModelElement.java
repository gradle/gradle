/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.model.internal.manage.state;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

public class ManagedModelElement<T> {

    private final Class<T> type;
    private final ImmutableSortedMap<String, ModelPropertyInstance<?>> properties;

    public ManagedModelElement(ModelSchema<T> schema) {
        this.type = schema.getType();
        ImmutableSortedMap.Builder<String, ModelPropertyInstance<?>> builder = ImmutableSortedMap.naturalOrder();
        for (ModelProperty<?> property : schema.getProperties().values()) {
            builder.put(property.getName(), ModelPropertyInstance.of(property));
        }
        this.properties = builder.build();
    }

    public Class<T> getType() {
        return type;
    }

    public <U> ModelPropertyInstance<U> get(Class<U> classType, String propertyName) {
        ModelPropertyInstance<?> modelPropertyInstance = properties.get(propertyName);
        Class<?> modelPropertyType = modelPropertyInstance.getMeta().getType().getRawClass();
        if (!modelPropertyType.equals(classType)) {
            throw new UnexpectedModelPropertyTypeException(propertyName, type, classType, modelPropertyType);
        }
        @SuppressWarnings("unchecked") ModelPropertyInstance<U> cast = (ModelPropertyInstance<U>) modelPropertyInstance;
        return cast;
    }

    ImmutableSortedMap<String, ModelPropertyInstance<?>> getProperties() {
        return properties;
    }
}
