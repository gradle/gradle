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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.instance.ModelInstantiator;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;

public class StrategyBackedModelInstantiator implements ModelInstantiator {

    private final Iterable<ModelInstantiatorStrategy> instantiators;
    private final ModelSchemaStore schemaStore;

    public StrategyBackedModelInstantiator(ModelSchemaStore schemaStore, ManagedProxyFactory proxyFactory, Instantiator instantiator) {
        this.schemaStore = schemaStore;
        this.instantiators = ImmutableList.of(
                new ManagedSetInstantiatorStrategy(instantiator),
                new StructModelInstantiator(proxyFactory)
        );
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
