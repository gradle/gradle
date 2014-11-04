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

import com.google.common.collect.ImmutableList;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

public class DefaultModelInstantiator implements ModelInstantiator {

    private final Iterable<ModelInstantiatorStrategy> instantiators = ImmutableList.of(
            new ManagedSetInstantiatorStrategy(),
            new StructModelInstantiator()
    );

    private final ModelSchemaStore schemaStore;

    public DefaultModelInstantiator(ModelSchemaStore schemaStore) {
        this.schemaStore = schemaStore;
    }

    public <T> T newInstance(ModelSchema<T> schema) {
        for (ModelInstantiatorStrategy strategy : instantiators) {
            T instance = strategy.newInstance(schema, schemaStore, this);
            if (instance != null) {
                return instance;
            }
        }

        throw new IllegalArgumentException("Unable to instantiate " + schema.getType());
    }

}
