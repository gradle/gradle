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

package org.gradle.model.internal.manage.schema.store;

import net.jcip.annotations.ThreadSafe;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.InvalidManagedModelElementTypeException;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;

@ThreadSafe
public abstract class ManagedModelPropertyFactory<T> implements ModelPropertyFactory<T> {
    private final ModelType<?> type;
    protected final ModelType<T> propertyType;
    protected final String propertyName;

    public ManagedModelPropertyFactory(ModelType<?> type, ModelType<T> propertyType, String propertyName) {
        this.type = type;
        this.propertyType = propertyType;
        this.propertyName = propertyName;
    }

    private ModelSchema<T> getModelSchema(ModelSchemaStore store, ModelType<?> type, ModelType<T> propertyType, String propertyName) {
        try {
            return store.getSchema(propertyType);
        } catch (InvalidManagedModelElementTypeException e) {
            throw new InvalidManagedModelElementTypeException(type, propertyName, e);
        }
    }

    public ModelProperty<T> create(ModelSchemaStore store) {
        final ModelSchema<T> modelSchema = getModelSchema(store, type, propertyType, propertyName);
        return doCreate(modelSchema);
    }

    protected abstract ModelProperty<T> doCreate(ModelSchema<T> modelSchema);
}
