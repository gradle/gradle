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

package org.gradle.model.internal.manage.instance;

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.collection.internal.DefaultManagedSet;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

public class ManagedSetInstantiatorStrategy implements ModelInstantiatorStrategy {

    public <T> T newInstance(ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator instantiator) {
        ModelType<T> type = schema.getType();
        if (type.getRawClass().equals(ManagedSet.class)) {
            ModelType<?> elementType = type.getTypeVariables().get(0);
            ModelSchema<?> elementTypeSchema = schemaStore.getSchema(elementType);
            return toSet(elementTypeSchema, instantiator);
        }

        return null;

    }

    private <T, E> T toSet(final ModelSchema<E> elementTypeSchema, final ModelInstantiator instantiator) {
        ManagedSet<E> set = new DefaultManagedSet<E>(new Factory<E>() {
            public E create() {
                return instantiator.newInstance(elementTypeSchema);
            }
        });

        return Cast.uncheckedCast(set);
    }

}
