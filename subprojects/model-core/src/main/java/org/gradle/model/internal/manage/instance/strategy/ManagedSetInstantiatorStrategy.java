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

package org.gradle.model.internal.manage.instance.strategy;

import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.collection.ManagedSet;
import org.gradle.model.internal.core.DefaultManagedSet;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;

public class ManagedSetInstantiatorStrategy implements ModelInstantiatorStrategy {

    private final Instantiator instantiator;

    public ManagedSetInstantiatorStrategy(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public <T> T newInstance(ModelSchema<T> schema, ModelSchemaStore schemaStore, ModelInstantiator modelInstantiator) {
        ModelType<T> type = schema.getType();
        if (type.getRawClass().equals(ManagedSet.class)) {
            ModelType<?> elementType = type.getTypeVariables().get(0);
            ModelSchema<?> elementTypeSchema = schemaStore.getSchema(elementType);
            return toSet(elementTypeSchema, modelInstantiator);
        }

        return null;

    }

    private <T, E> T toSet(final ModelSchema<E> elementTypeSchema, final ModelInstantiator modelInstantiator) {
        Factory<E> modelFactory = new Factory<E>() {
            public E create() {
                return modelInstantiator.newInstance(elementTypeSchema);
            }
        };
        return Cast.uncheckedCast(instantiator.newInstance(DefaultManagedSet.class, modelFactory));
    }

}
